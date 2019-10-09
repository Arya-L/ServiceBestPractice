package com.example.servicebestpractice;

import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadTask extends AsyncTask<String, Integer, Integer> {

    public static final int TYPE_SUCCESS = 0;
    public static final int TYPE_FAILED = 1;
    public static final int TYPE_PAUSE = 2;
    public static final int TYPE_CANCELED = 3;

    private DownloadListener listener;
    private boolean isCanceled = false;
    private boolean isPaused = false;
    private int lastProgress;


    public DownloadTask(DownloadListener listener){
        //下载状态 回调
        this.listener = listener;
    }


    // 下载逻辑
    @Override
    protected Integer doInBackground(String... parms) {
        InputStream is = null;
        RandomAccessFile savedFile = null;
        File file = null;
        try {
            long downloadedLength = 0;
            String downloadUrl = parms[0];
            String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));
            String directory = Environment.getExternalStoragePublicDirectory
                    (Environment.DIRECTORY_DOWNLOADS).getPath();
            file = new File(directory + fileName);
            if (file.exists()) {
                downloadedLength = file.length();
            }
            long contentLength = getContentLength(downloadUrl);  //获取待下载文件的总长度
            if (contentLength == 0) {
                return TYPE_FAILED;      //如果文件长度为0，说明文件有问题
            } else if (contentLength == downloadedLength) {
                return TYPE_SUCCESS;     //如果文件长度等于已下载文件长度，说明文件有问题
            }
            OkHttpClient client = new OkHttpClient();
            // 请求中添加一个header，用于告诉服务器我们要从那个字节开始下载，因为已经下载的部分不需要再重新下载了
            Request request = new Request.Builder()
                    .addHeader("RANGE", "bytes=" + downloadedLength + "-")
                    .url(downloadUrl)
                    .build();
            Response response = client.newCall(request).execute();
            if (response != null) {
                // 使用JAVA的文件流方式，不断从网络上读取数据，不断写入到本地，直到文件全部下载完成
                is = response.body().byteStream();
                savedFile = new RandomAccessFile(file, "rw");
                savedFile.seek(downloadedLength);
                byte[] b = new byte[1024];
                int total = 0;
                int len;
                //判断用户是否触发暂停或者取消操作
                while ((len = is.read(b)) != -1) {
                    if (isCanceled) {
                        return TYPE_CANCELED;
                    } else if (isPaused) {
                        return TYPE_PAUSE;
                    } else {
                        // 实时计算当前的下载进度,然后调用publishProgress进行通知
                        total += len;
                        savedFile.write(b, 0, len);
                        int progress = (int) ((total + downloadedLength)
                                * 100 / contentLength);
                        publishProgress(progress);
                    }
                }
                response.body().close();
                return TYPE_SUCCESS;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
                if (savedFile != null) {
                    savedFile.close();
                }
                if (isCanceled && file != null) {
                    file.delete();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return TYPE_FAILED;
    }


    //更新当前的下载进度
    @Override
    protected void onProgressUpdate(Integer... values) {
        int progress = values[0];  //当前下载进度
        // 与上一次的下载进度进行对比，有变化则调用DownloadListener通知下载进度更新
        if (progress > lastProgress){
            listener.onProgress(progress);
            lastProgress = progress;
        }
    }


    //用于通知最终的下载结果，根据参数中传入的下载状态来进行回调。（监听者模式）
    @Override
    protected void onPostExecute(Integer status) {
        switch (status){
            case TYPE_SUCCESS:
                listener.onSuccess();
                break;
            case TYPE_FAILED:
                listener.onFailed();
                break;
            case TYPE_PAUSE:
                listener.onPaused();
                break;
            case TYPE_CANCELED:
                listener.onCanceled();
                break;
            default:
                break;
        }
    }


    public void pauseDownload(){
        isPaused = true;
    }


    public void cancelDownload(){
        isCanceled = true;
    }


    private long getContentLength (String downloadUrl) throws IOException{
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(downloadUrl)
                .build();
        Response response = client.newCall(request).execute();
        if (response != null && response.isSuccessful()){
            long contentLength = response.body().contentLength();
            response.body().close();
            return contentLength;
        }
        return 0;
    }

}
