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
        if (backgroundProcessingFailed)
        {
            activityContext.csvConversionFailed();
        }
        else {
            //Trigger HTTP Async thread after this thread is complete
            Log.d("RTL_LOG", "Starting HTTP Async thread...");
            activityContext.beginUploadtoMongoDB();
        }
    }

    private void convert(String dirName, String batchID, float altitude, double latitude, double longitude, String integrationInterval) throws IOException, ParseException {
        Log.d("RTL_LOG", "Finding .csv file in directory...");
        String csvFile = findFile(dirName, batchID);

        if (!csvFile.equals("NOTFOUND")) { // only proceed when there is a valid csv file
            Log.d("RTL_LOG", "Found .csv file, starting conversion...");
            BufferedReader br = new BufferedReader(new FileReader(csvFile));
            Map<String, String> integrationsList = new HashMap<>();
            String line;
            String csvSplitBy = ", ";
            boolean integrationExists;

            //Iterate through entire CSV file
            Log.d("RTL_LOG", "Iterating through .csv file...");
            while ((line = br.readLine()) != null) {
                String[] entry = line.split(csvSplitBy); //Use comma-space as separator
                integrationExists = false;

                //Convert datetime to unix timestamp
				String datetime = entry[0] + entry[1];
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-ddHH:mm:ss");
                long unixTime =  sdf.parse(datetime).getTime() / 1000;
                String unix = String.valueOf(unixTime);

                //Set boolean if a key already exists in the HashMap
                for (String key : integrationsList.keySet()) {
                    if (key.equals(unix)) {
                        integrationExists = true;
                        break;
                    }
                }

                //If a key already exists in the HashMap
                if (integrationExists) {
                    String json;
                    json = integrationsList.get(unix);
                    int frequencyLow = Integer.parseInt(entry[2]);
                    int frequencyStep = (int)Double.parseDouble(entry[4]);

                    String valuesHeader = "{\"frequencyLow\":\"" + frequencyLow + "\",\"frequencyHigh\":\"" + entry[3] + "\",\"frequencyStep\":\"" + frequencyStep + "\",\"metricValues\":[";
                    String valuesBody = "{\"" + Integer.toString(frequencyLow) + "\":" + entry[6] + "}," +  "{\"" + Integer.toString(frequencyLow + (frequencyStep)) + "\":" + entry[7] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 2)) + "\":" + entry[8] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 3)) + "\":" + entry[9] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 4)) + "\":" + entry[10] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 5)) + "\":" + entry[11] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 6)) + "\":" + entry[12] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 7)) + "\":" + entry[13] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 8)) + "\":" + entry[14] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 9)) + "\":" + entry[15] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 10)) + "\":" + entry[16] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 11)) + "\":" + entry[17] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 12)) + "\":" + entry[18] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 13)) + "\":" + entry[19] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 14)) + "\":" + entry[20] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 15)) + "\":" + entry[21] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 16)) + "\":" + entry[22] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 17)) + "\":" + entry[23] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 18)) + "\":" + entry[24] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 29)) + "\":" + entry[25] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 20)) + "\":" + entry[26] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 21)) + "\":" + entry[27] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 22)) + "\":" + entry[28] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 23)) + "\":" + entry[29] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 24)) + "\":" + entry[30] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 25)) + "\":" + entry[31] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 26)) + "\":" + entry[32] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 27)) + "\":" + entry[33] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 28)) + "\":" + entry[34] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 29)) + "\":" + entry[35] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 30)) + "\":" + entry[36] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 31)) + "\":" + entry[37] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 32)) + "\":" + entry[38] + "}]}";

                    json = json + "," + valuesHeader + valuesBody;
                    integrationsList.put(unix, json);
                }
                //Create a new HashMap entry for this key
                else {
                    int frequencyLow = Integer.parseInt(entry[2]);
                    int frequencyStep = (int)Double.parseDouble(entry[4]);

                    String integrationHeader = "{\"unixTimestamp\":\"" + unixTime + "\",\"date\":\"" + entry[0] + "\",\"time\":\"" + entry[1] + "\",\"totalSamples\":\"" + entry[5] + "\",\"metricSeries\":[";
                    String valuesHeader = "{\"frequencyLow\":\"" + frequencyLow + "\",\"frequencyHigh\":\"" + entry[3] + "\",\"frequencyStep\":\"" + frequencyStep + "\",\"metricValues\":[";
                    String valuesBody = "{\"" + Integer.toString(frequencyLow) + "\":" + entry[6] + "}," +  "{\"" + Integer.toString(frequencyLow + (frequencyStep)) + "\":" + entry[7] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 2)) + "\":" + entry[8] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 3)) + "\":" + entry[9] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 4)) + "\":" + entry[10] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 5)) + "\":" + entry[11] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 6)) + "\":" + entry[12] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 7)) + "\":" + entry[13] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 8)) + "\":" + entry[14] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 9)) + "\":" + entry[15] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 10)) + "\":" + entry[16] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 11)) + "\":" + entry[17] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 12)) + "\":" + entry[18] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 13)) + "\":" + entry[19] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 14)) + "\":" + entry[20] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 15)) + "\":" + entry[21] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 16)) + "\":" + entry[22] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 17)) + "\":" + entry[23] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 18)) + "\":" + entry[24] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 29)) + "\":" + entry[25] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 20)) + "\":" + entry[26] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 21)) + "\":" + entry[27] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 22)) + "\":" + entry[28] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 23)) + "\":" + entry[29] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 24)) + "\":" + entry[30] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 25)) + "\":" + entry[31] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 26)) + "\":" + entry[32] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 27)) + "\":" + entry[33] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 28)) + "\":" + entry[34] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 29)) + "\":" + entry[35] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 30)) + "\":" + entry[36] + "}," + "{\"" + Integer.toString(frequencyLow + (frequencyStep * 31)) + "\":" + entry[37] + "}," +
                            "{\"" + Integer.toString(frequencyLow + (frequencyStep * 32)) + "\":" + entry[38] + "}]}";
                    String integrationJSON = integrationHeader + valuesHeader + valuesBody;

                    integrationsList.put(unix, integrationJSON);
                }
            }

            StringBuilder sb = new StringBuilder();
            SortedSet<String> keys = new TreeSet<>(integrationsList.keySet()); //Sort HashMap by key (unix timestamp)

            //Iterate through HashMap to build JSON
            Log.d("RTL_LOG", "Building JSON file from HashMap...");
            for (String key : keys) {
                sb.append(integrationsList.get(key)); //Build JSON Body
                sb.append("]},"); //Close off JSON Array and JSON Object
            }

            //Add JSON header fields
            String jsonHeader = "{\"BATCH_ID\":\"" + batchID + "\",\"altitude\":\"" + altitude + "\",\"latitude\":\"" + latitude +
                    "\",\"longitude\":\"" + longitude + "\",\"integrationInterval\":\"" + integrationInterval + "\",\"integrations\":[";
            sb.setLength(sb.length() -1); //Remove final comma as there are no more JSON objects to be added
            sb.append("]}"); //Close off JSON Array and JSON Object

            //Export JSON
            FileWriter fr = new FileWriter(dirName + "/" + batchID + ".json");
            fr.write(jsonHeader + sb.toString());
            Log.d("RTL_LOG", "File exported to" + dirName + "/" + batchID + ".json");

            fr.close();
            br.close();
        }
        else
        {
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
