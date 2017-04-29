package com.sdrtouch.rtlsdr;

import android.os.AsyncTask;
import android.util.Log;

import java.util.*;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Created by Jackie on 28/11/2016.
 * This class is responsible for converting an RTL_POWER .csv output file into a .json file
 * which is more suitable for ingest into MongoDB.
 */

public class CsvConverter extends AsyncTask<String, Void, Void> {
    private String dirName;
    private String batchID;
    private float altitude;
    private double latitude;
    private double longitude;
    private String integrationInterval;
    private StreamActivity activityContext;
    private Boolean backgroundProcessingFailed=false;

    public CsvConverter(StreamActivity streamActivity, String dirName, String batchID, float altitude, double latitude, double longitude, String integrationInterval) {
        this.activityContext = streamActivity;
        this.dirName = dirName;
        this.batchID = batchID;
        this.altitude = altitude;
        this.latitude = latitude;
        this.longitude = longitude;
        this.integrationInterval = integrationInterval;
    }

    @Override
    protected Void doInBackground(String[] params) {
        try {
            convert(dirName, batchID, altitude, latitude, longitude, integrationInterval);
        } catch (Exception e) {
            e.printStackTrace();
            backgroundProcessingFailed=true;
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void v) {
        if (backgroundProcessingFailed) {
            activityContext.csvConversionFailed();
        }
        else {
            //Trigger HTTP Async thread after this thread is complete
            Log.d("RTL_LOG", "Starting HTTP Async thread...");
            activityContext.beginUploadtoMongoDB();
        }
    }

    private void convert(String dirName, String batchID, float altitude, double latitude, double longitude, String integrationInterval) throws IOException, ParseException {
        // Get CSV file location
        String csvFile = findFile(dirName, batchID);

        // Only proceed when there is a valid csv file
        if (!csvFile.equals("NOTFOUND")) {
            Log.d("RTL_LOG", "Found .csv file, starting conversion...");
            BufferedReader br = new BufferedReader(new FileReader(csvFile));
            // One entry in the HashMap for each spectrum sweep
            Map<String, String> integrationsList = new HashMap<>();
            String line;
            String csvSplitBy = ", ";
            boolean integrationExists;

            // Iterate through CSV one row at a time
            while ((line = br.readLine()) != null) {
                // Populate string array entry, based on the value of each column in the row
                String[] entry = line.split(csvSplitBy);
                integrationExists = false;

                //Convert first 2 columns into a to unix timestamp, to be used as a key
				String datetime = entry[0] + entry[1];
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-ddHH:mm:ss");
                long unixTime =  sdf.parse(datetime).getTime() / 1000;
                String unix = String.valueOf(unixTime);

                // Iterate though integration HashMap and
                // set boolean if a key (unix) already exists in the HashMap
                for (String key : integrationsList.keySet()) {
                    if (key.equals(unix)) {
                        integrationExists = true;
                        break;
                    }
                }

                //If a key already exists in the HashMap
                if (integrationExists) {
                    String json;
                    // Retrieve existing JSON for the same integration
                    json = integrationsList.get(unix);
                    // Parse values from CSV
                    int frequencyLow = Integer.parseInt(entry[2]);
                    int frequencyStep = (int)Double.parseDouble(entry[4]);
                    // Translate values into JSON format
                    String valuesHeader = "{\"frequencyLow\":\"" + frequencyLow + "\",\"frequencyHigh\":\"" + entry[3] + "\",\"frequencyStep\":\"" + frequencyStep + "\",\"metricValues\":[";
                    String valuesBody = "{\"" + Integer.toString(frequencyLow) + "\":" + entry[6] + "}]}";
                    // Append new JSON value to existing JSON
                    json = json + "," + valuesHeader + valuesBody;
                    // Save appended JSON to HashMap
                    integrationsList.put(unix, json);
                }
                // Else loop for when an integration does not exist in the HashMap
                // Create a new HashMap entry for this key
                // Populate HashMap with JSON containing integration header
                else {
                    // Parse values from CSV
                    int frequencyLow = Integer.parseInt(entry[2]);
                    int frequencyStep = (int)Double.parseDouble(entry[4]);
                    // Translate values into JSON format
                    String integrationHeader = "{\"unixTimestamp\":\"" + unixTime + "\",\"date\":\"" + entry[0] + "\",\"time\":\"" + entry[1] + "\",\"totalSamples\":\"" + entry[5] + "\",\"metricSeries\":[";
                    String valuesHeader = "{\"frequencyLow\":\"" + frequencyLow + "\",\"frequencyHigh\":\"" + entry[3] + "\",\"frequencyStep\":\"" + frequencyStep + "\",\"metricValues\":[";
                    String valuesBody = "{\"" + Integer.toString(frequencyLow) + "\":" + entry[6] + "}]}";
                    // Build JSON string
                    String integrationJSON = integrationHeader + valuesHeader + valuesBody;
                    // Save JSON to HashMap
                    integrationsList.put(unix, integrationJSON);
                }
            }

            // CSV has finished iterating, begin building JSON output file...
            StringBuilder sb = new StringBuilder();
            //Sort HashMap by key (unix timestamp), earliest to latest
            SortedSet<String> keys = new TreeSet<>(integrationsList.keySet());

            //Iterate through HashMap to build JSON
            for (String key : keys) {
                //Build JSON Body
                sb.append(integrationsList.get(key));
                //Split or close each integration using below seperator
                sb.append("]},");
            }

            //Build JSON header
            String jsonHeader = "{\"BATCH_ID\":\"" + batchID + "\",\"altitude\":\"" + altitude + "\",\"latitude\":\"" + latitude +
                    "\",\"longitude\":\"" + longitude + "\",\"integrationInterval\":\"" + integrationInterval + "\",\"integrations\":[";
            //Remove final comma as there are no more JSON objects to be added
            sb.setLength(sb.length() -1);
            //Close off JSON Array and JSON Object
            sb.append("]}");

            //Export JSON to the .json file
            FileWriter fr = new FileWriter(dirName + "/" + batchID + ".json");
            fr.write(jsonHeader + sb.toString());
            Log.d("RTL_LOG", "File exported to" + dirName + "/" + batchID + ".json");

            fr.close();
            br.close();
        } else {
            backgroundProcessingFailed=true;
        }
    }

    private String findFile(String dirName, String batchID) {
        File dir = new File(dirName);
        File[] files = dir.listFiles();
        String csvFileName = "NOTFOUND";

        for (File file : files) {
            if (file.getName().equals(batchID + ".csv")) {
                csvFileName = dirName + "/" + file.getName();
            }
        }
        return csvFileName;
    }
}
