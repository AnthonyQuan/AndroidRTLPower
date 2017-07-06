package com.sdrtouch.rtlsdr;

import android.os.AsyncTask;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Jackie on 26/03/2016.
 * This class is responsible for consolidating the Firebase token, latitude, and logitide values, before
 * POSTing the contents to MongoDB via a URI endpoint.
 */

class PostToken extends AsyncTask<String, Void, Object> {
    private String androidID;
    private double latitude;
    private double longitude;

    PostToken(String androidID, double latitude, double longitude) {
        this.androidID = androidID;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Override
    protected Void doInBackground(String[] params) {
        try {
            String refreshToken = FirebaseInstanceId.getInstance().getToken();
            String date = generateDate();
            executeRequest(date, refreshToken);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String generateDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
        Date date = new Date();
        return sdf.format(date);
    }

    private void executeRequest(String date, String refreshToken) throws Exception {
        String responseData;
        String url = "http://spectrumdatabase.org/addrecord/device";

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
        wr.writeBytes("{\"token\":\"" + refreshToken + "\",\"deviceID\":\"" + androidID
                + "\",\"latitude\":\"" + latitude + "\",\"longitude\":\"" + longitude
                + "\",\"date\":\"" + date + "\"}");
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
}
