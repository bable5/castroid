/*
   Copyright 2010 Christopher Kruse and Sean Mooney

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. 
 */
package com.cornerofseven.castroid.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.cornerofseven.castroid.dialogs.DownloadDialog;

/**
 * A service to allow asynchronous downloads to run
 * and not block the main thread.
 * 
 * <h3>Lifecycle</h3>
 * <p>
 * Both {@see #onStart(Intent, int)} and {@see #onStartCommand(Intent, int, int)}
 * delegate to handleCommand, which takes care of starting a new download on
 * a seperate thread.
 * </p>
 * 
 * 
 * <h3>Synchronization Policy</h3>
 * <p>The download manager has multiple threads that must interact
 * to operate the Service properly.  Any method accesses the {@see #runningDownloads} 
 * field must be synchronized, otherwise a potential race condition exists
 * between dispatching a new download and shutting down the service 
 * when the last active download finishes. 
 * </p>
 * <p> The service shutdown policy is to shutdown when a download finishes
 * and the set of running downloads is also empty. The synchronization policy 
 * is that any method, which directly or
 * indirectly access the {@see #runningDownloads} field must be synchronized.
 * </p>
 * @author Sean Mooney
 * @since v0.1
 */
public class DownloadService extends Service{

    /**
     * Intent data for where to download the link.
     */
    public static final String INT_DOWNLOAD_FOLDER = "download_dest";
    
    static final String TAG = "DownloadService";

    /**
     * List of running downloads. When this is empty the 
     * service can be shutdown.
     */
    ArrayList<AsyncDownload> runningDownloads;

    /**
     * Handles all the messages from running downloads.
     */
    ServiceMsgHandler mMessageHandler;

    /**
     * (non-Javadoc)
     * @see android.app.Service#onCreate()
     */
    @Override
    public synchronized void onCreate(){
        runningDownloads = new ArrayList<DownloadService.AsyncDownload>();
        mMessageHandler  = new ServiceMsgHandler(this);
        super.onCreate();
    }

    @Override
    /**
     * Synchronize the startService method to make
     * sure that the service is restarted if the
     * last running download finishes and stops
     * the service when startService is called.
     */
    public synchronized ComponentName startService(Intent intent){
        return super.startService(intent);
    }
    
    @Override
    public synchronized void onDestroy(){
        if(!runningDownloads.isEmpty()){
            Log.w(TAG, "Destroying the download service with downloads pending");
        }
        runningDownloads = null;
        mMessageHandler = null;
    }

    //For pre-2.0 platforms
    /*
     * (non-Javadoc)
     * @see android.app.Service#onStart(android.content.Intent, int)
     */
    @Override
    public synchronized void onStart(Intent intent, int startId){
        handleCommand(intent, startId);
    }

    /*
     * (non-Javadoc)
     * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
     */
    @Override
    /**
     * Download the file. Intent caller is responsible for
     * setting the link URI as the intent data, the destination folder/dir
     * as an extra, with key {@see #INT_DOWNLOAD_FOLDER}
     */
    public synchronized int onStartCommand(Intent intent, int flags, int startId){
        handleCommand(intent, startId);
        return START_STICKY;
    }

    /**
     * Handle a download intent.
     * 
     * To handle a download intent, start a new async download object,
     * give it the URL from the the intent, start the download thread.
     * 
     * If the there is no URL in the intent, does nothing.
     * 
     * @param intent
     */
    protected synchronized void handleCommand(Intent intent, int startId){

        File dlDir;
        Uri dlUri;
        
        //pull the download dir from the intent.
        Bundle bundle = intent.getExtras();
        dlDir = new File(bundle.getString(INT_DOWNLOAD_FOLDER));
        
        dlUri = intent.getData();
        
        //pull the download URL from the intent.
        
        if(!dlDir.exists()){
            Toast.makeText(this, dlDir.getAbsoluteFile() + " does not exist.", Toast.LENGTH_LONG).show();
            return;
        }

        if(!dlDir.canWrite()){
            Toast.makeText(this, dlDir.getAbsoluteFile() + " is not writtable.", Toast.LENGTH_LONG).show();
            return;
        }

        AsyncDownload download = new AsyncDownload(dlDir, mMessageHandler);
        runningDownloads.add(download);

        //TODO: What happens if the downloadLink is invalid?
        if(dlUri == null){
            Toast.makeText(this, "Download uri is null", Toast.LENGTH_LONG).show();
            return;
        }
        
        download.execute(dlUri);
    }

    /**
     * Finish up the download.
     * 
     * <p>
     * When a download finishes, remove from the list of
     * running tracked downloads.
     * </p>
     * <p>
     * Accesses the download list.
     * </p>
     * @param download
     */
    protected synchronized void finishDownload(AsyncDownload download){
        runningDownloads.remove(download);
        if(runningDownloads.isEmpty()){
            Log.i(TAG, "Shutting down the download service.");
            stopSelf();
        }
    }

    /* (non-Javadoc)
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    /**
     * BINDING NOT SUPPORTED.
     * @return null.
     */
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class AsyncDownload extends AsyncTask<Uri, Integer, Long>{
        static final String TAG = "Download";

        File dlDir;
        Handler mHandler;

        
        //ProgressDialog mProgressDialog;
        public AsyncDownload(File dlDir, Handler handler){
            this.dlDir = dlDir;
            this.mHandler = handler;
        }
        
        /* (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Long doInBackground(Uri... uris) {

            Long totalDownloadSize = 0L;
            for(Uri uri : uris ){
                if(isCancelled())break;

                totalDownloadSize += downloadUri(uri);
            }


            return totalDownloadSize;
        }

        @Override
        public void onCancelled(){
            Bundle b = new Bundle();
            b.putBoolean(DownloadDialog.PROGRESS_DONE, false);
            signalHandler(mHandler, DownloadDialog.WHAT_CANCELED, b);

            cleanup();
        }

        @Override
        public void onProgressUpdate(Integer... progresses){
            Bundle b = new Bundle();
            b.putInt(DownloadDialog.PROGRESS_UPDATE, progresses[0]);
            signalHandler(mHandler, DownloadDialog.WHAT_UPDATE, b);
        }

        @Override
        public void onPostExecute(Long result){
            //TODO: log message to handler
            //Toast.makeText(mActivity, "Downloaded " + result, Toast.LENGTH_SHORT).show();
            cleanup();
        }

        /**
         * Display a Toast message describing the problem.
         * @param e
         */
        private void onError(Exception e){
            //TODO: log message to handler
            //Toast.makeText(mContext, "Error during download!\n" + e.getMessage(), Toast.LENGTH_LONG).show();
            finishDownload(this);
        }

        private void signalHandler(Handler handler, int msgType, Bundle data){
            if(handler != null) {
                Message msg = handler.obtainMessage(msgType);
                msg.setData(data);
                handler.sendMessage(msg);
            }
        }

        /**
         * Null out all the refs, we won't need them again.
         * Should only be called by onPostExecute or onCancelled.
         * Semantics of AsyncTask are such that after either of these
         * methods are called, the task should not run again.
         * 
         * Calls finishDownload in the parent context to remove itself
         * from the list of running tasks.
         */
        private void cleanup(){
            mHandler = null;
            dlDir = null;
            finishDownload(this);
        }

        /**
         * Download a single file
         * @param dlUrl
         * @return
         */
        public Long downloadUri(Uri dlUri){
            String fileName = dlUri.getLastPathSegment();
            File dataDir = dlDir;

            if(dlDir == null) return 0L;

            if(!dataDir.canWrite()){
                Log.e(TAG, "Cannot write to " + dataDir.getAbsolutePath());
                return 0L;
            }

            //we only need to create the directory if it doesn't exist...how silly of me...
            if(!dataDir.exists()) {
                if(!dataDir.mkdirs()) {
                    Log.e(TAG, "Unable to create " + dataDir.getAbsolutePath());
                    return 0L;
                }
            }

            return  downloadFile(dlUri.toString(), new File(dataDir, fileName));
        }


        /**
         * Download a given URL to a file.
         * based on stack overflow example: @{link http://stackoverflow.com/questions/3287795/android-download-service}
         */
        private Long downloadFile( String link, File dest)
        {
            BufferedInputStream bInputStream = null;
            BufferedOutputStream bOutputStream = null;
            int downloadSize = 0;

            try{
                URL url = new URL(link);
                FileOutputStream fileOutput = new FileOutputStream(dest);

                HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setDoInput(true);

                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();

                //TODO: Tune buffer sizes
                final int BUFFER_SIZE = 100 * 1024; //100K buffer;

                //buffer both streams.
                bInputStream = new BufferedInputStream(inputStream, BUFFER_SIZE);
                bOutputStream = new BufferedOutputStream(fileOutput, BUFFER_SIZE);

                int totalSize = urlConnection.getContentLength();
                //TODO: How big of a file before this rolls over and is meaningless, or do we need a long?

                int bytesSinceLastLog = 0;

                byte[] buffer = new byte[1024];
                int bytesRead = 0;
                final int K_PER_UPDATE = (1024 * 100); //update every tenth meg downloaded

                //signal the max download size
                Bundle b = new Bundle();
                b.putInt(DownloadDialog.PROGRESS_MAX, totalSize);
                signalHandler(mHandler, DownloadDialog.WHAT_START, b);

                //we'll do this the old fashioned way...copy buffered bytes from inputstream to output stream
                while(!isCancelled() && ( bytesRead = bInputStream.read(buffer)) > 0){
                    bOutputStream.write(buffer, 0, bytesRead);
                    downloadSize += bytesRead;
                    bytesSinceLastLog += bytesRead;


                    if(bytesSinceLastLog > K_PER_UPDATE){
                        float per = (float)downloadSize / (float)totalSize;
                        //Log.i(TAG, "Downloaded " + downloadSize + " bytes " + per + "%");
                        bytesSinceLastLog = 0;
                        //int msgArg = (int)(per * 100);
                        publishProgress(downloadSize);
                    }
                }
            } catch (MalformedURLException e) {  
                onError(e);
            } catch (IOException e) { 
                onError(e);
            }
            finally{
                if(bOutputStream != null)
                    try {
                        bOutputStream.close();
                    } catch (IOException e) {}//ignore, grumble grumble grumble

                    if(bInputStream != null)
                        try {
                            bInputStream.close();
                        } catch (IOException e) {}
            }
            return new Long(downloadSize);
        }
    }

    static class ServiceMsgHandler extends Handler{
        public static final int WHAT_START = 1;
        public static final int WHAT_UPDATE = 2;
        public static final int WHAT_DONE = 3;
        public static final int WHAT_CANCELED = 4;

        public static final String PROGRESS_MAX = "max";
        public static final String PROGRESS_UPDATE = "total";
        public static final String PROGRESS_DONE = "done";

        private Context mContext;

        public ServiceMsgHandler(Context context){
            this.mContext = context;
        }
        
        

        /*
         * (non-Javadoc)
         * 
         * @see android.os.Handler#handleMessage(android.os.Message)
         */
        @Override
        public void handleMessage(Message msg) {

            Context context = mContext;

            super.handleMessage(msg);
            switch (msg.what) {
                case WHAT_START:
                    int max = msg.getData().getInt(PROGRESS_MAX);
                    //                setMax(max);
                    //                setProgress(0);
                    Log.i(TAG, "Download Max: " + max);
                    break;
                case WHAT_UPDATE:
                    int total = msg.getData().getInt(PROGRESS_UPDATE);
                    //setProgress(total);
                    Log.i(TAG, "Download Total: " + total);
                    break;
                case WHAT_DONE:
                    boolean success = msg.getData().getBoolean(PROGRESS_DONE);
                    if(!success){
                        //Toast.makeText(context, "Download Failed", Toast.LENGTH_SHORT).show();
                        Log.i(TAG, "Download Failed");
                    }
                case WHAT_CANCELED:
            }
        }
    }
}