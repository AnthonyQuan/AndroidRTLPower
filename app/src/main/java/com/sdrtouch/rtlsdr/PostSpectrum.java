package com.sdrtouch.rtlsdr;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by Jackie on 28/11/2016.
 * This class is responsible for locating the .json file after conversion and POSTing
 * the contents to MongoDB via a URI endpoint.
 */

class PostSpectrum extends AsyncTask<String, Void, Object> {
    private String dirName;
    private String batchID;
    private StreamActivity activityContext;
    private boolean backgroundProcessingFailed=false;

    PostSpectrum(StreamActivity streamActivity, String dirName, String batchID) {
        this.activityContext = streamActivity;
        this.dirName = dirName;
        this.batchID = batchID;
    }

    @Override
    protected Void doInBackground(String[] params) {
        try {
            File jsonFile = findFile(dirName, batchID);

            if (jsonFile.canRead()) { // only proceed when there is a valid json file
                Log.d("RTL_LOG", "Found .json file, initiating HTTP POST request...");
                FileInputStream fis = new FileInputStream(jsonFile);
                String jsonData = convertStreamToString(fis);
                fis.close();
                executeRequest(jsonData);
            } else {
                backgroundProcessingFailed=true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void executeRequest(String jsonData) throws Exception {
        String responseData;
        String url = "http://ec2-13-55-90-132.ap-southeast-2.compute.amazonaws.com/addrecord/spectrum";

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("POST");

        //Request Headers
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 6.0.1; HTC 10 Build/MMB29M) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.85 Mobile Safari/537.36");
        con.setRequestProperty("Accept-Language", "en-AU,en;q=0.8");
        con.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        con.setRequestProperty("Accept", "*/*");

        //Request Body
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(jsonData);
        wr.flush();
        wr.close();

        //Parse Response
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //Store and Log Response
        responseData = response.toString();
        Log.d("RTL_LOG","Server response: " + responseData);
    }

    private static File findFile(String dirName, String batchID) {
        File dir = new File(dirName);
        File[] files = dir.listFiles();
        File jsonFile = null;

        for (File file : files) {
            if (file.getName().equals(batchID + ".json"))
                jsonFile = file;
        }
        return jsonFile;
    }

    private static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    @Override
    protected void onPostExecute(Object result) {
        if (backgroundProcessingFailed)
            activityContext.uploadFailed();
        else
            activityContext.uploadSucessful();
    }
}
