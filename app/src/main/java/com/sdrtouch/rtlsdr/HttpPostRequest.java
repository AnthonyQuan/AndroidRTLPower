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
 */

public class HttpPostRequest extends AsyncTask<String, Void, String> {
    private String dirName;
    private String batchID;

    public HttpPostRequest(String dirName, String batchID) {
        this.dirName = dirName;
        this.batchID = batchID;
    }

    @Override
    protected String doInBackground(String[] params) {
        String data = null;
        try {
            Log.d("RTL_LOG", "Finding .json file in directory...");
            File jsonFile = findFile(dirName, batchID);

            if (jsonFile.canRead()) { // only proceed when there is a valid json file
                Log.d("RTL_LOG", "Found .json file, initiating HTTP POST request...");
                FileInputStream fis = new FileInputStream(jsonFile);
                String jsonData = convertStreamToString(fis);
                fis.close();
                data = executeRequest(jsonData);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }

    @Override
    protected void onPostExecute(String result) {
        Log.d("RTL_LOG","Response body: " + result);
    }

    private String executeRequest(String jsonData) throws Exception {
        String responseData;
        String url = "http://ec2-52-64-226-30.ap-southeast-2.compute.amazonaws.com:9000/addrecord/spectrum";

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
        int responseCode = con.getResponseCode();
        Log.d("RTL_LOG","Sending 'POST' request to URL: " + url);
        Log.d("RTL_LOG","Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //Store Response
        responseData = response.toString();
        return responseData;
    }

    private static File findFile(String dirName, String batchID) {
        File dir = new File(dirName);
        File[] files = dir.listFiles();
        File jsonFile = null;

        for (File file : files) {
            if (file.getName().equals(batchID + ".json")) {
                jsonFile = file;
            }
        }
        return jsonFile;
    }

    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
