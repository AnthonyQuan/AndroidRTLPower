package com.sdrtouch.rtlsdr;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by Jackie on 15/02/2017.
 */

public class SyncTime extends AsyncTask<Object, Object, Object> {
    private StreamActivity activityContext;

    public SyncTime(StreamActivity activityContext) {
        this.activityContext = activityContext;
    }

    @Override
    protected Object doInBackground(Object... arg0) {
        try {
            long executionTime = getSyncTime();
            long currentTime = System.currentTimeMillis();
            Log.d("RTL_LOG", "Sync time: " + executionTime);
            Log.d("RTL_LOG", "Current time: " + currentTime);
            Log.d("RTL_LOG", "Sleeping for: " + Long.toString((executionTime - currentTime)) + " milliseconds");
            for (int i=0; i<30;i++)
            {
                if (activityContext.isRunning)
                {
                    Thread.sleep(Math.abs((executionTime - currentTime))/30);
                }
                else
                {
                    return null;
                }
            }
            Log.d("RTL_LOG", "Sleeping complete");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(Object o) {
        activityContext.beginSpectrumRecording();
    }

    private long getSyncTime() throws IOException {
        Log.d("RTL_LOG","Synchronising with server");
        String url = "http://ec2-13-55-90-132.ap-southeast-2.compute.amazonaws.com/synctime";

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");

        //Request Headers
        //con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 6.0.1; HTC 10 Build/MMB29M) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.85 Mobile Safari/537.36");
        //con.setRequestProperty("Accept-Language", "en-AU,en;q=0.8");
        //con.setRequestProperty("Accept", "*/*");

        //Parse Response
        int responseCode = con.getResponseCode();
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //Store Response
        return Long.valueOf(response.toString());
    }
}
