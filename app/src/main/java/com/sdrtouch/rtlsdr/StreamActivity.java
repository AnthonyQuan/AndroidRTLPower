/*
 * rtl_tcp_andro is a library that uses libusb and librtlsdr to
 * turn your Realtek RTL2832 based DVB dongle into a SDR receiver.
 * It independently implements the rtl-tcp API protocol for native Android usage.
 * Copyright (C) 2016 by Martin Marinov <martintzvetomirov@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sdrtouch.rtlsdr;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.location.LocationServices;
import com.sdrtouch.tools.DialogManager;
import com.sdrtouch.tools.UsbPermissionHelper;
import com.sdrtouch.tools.UsbPermissionObtainer;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import marto.rtl_tcp_andro.R;

public class StreamActivity extends FragmentActivity implements ConnectionCallbacks{

    private boolean isRunning = false;
    private String batchID = null;
    private File dirName = new File(Environment.getExternalStorageDirectory() + File.separator + "RTL_POWER");
    GoogleApiClient GoogleApiClient;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    //location permissions
    private static final int REQUEST_LOCATION = 2;
    private static String[] PERMISSIONS_LOCATION = {android.Manifest.permission.ACCESS_FINE_LOCATION};

    // loads the c library
    static {
        System.loadLibrary("rtlSdrAndroid");
    }

    //c methods
    public native String stringFromJNI(String[] argv);
    public native void passFDandDeviceName(int fd_, String path_);
    public native void staphRTLPOWER();
    public native int readExecutionFinished();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);

        //experimental code to update textview with logs start
        TextView logView = (TextView) findViewById(R.id.textView);
        logView.setMovementMethod(new ScrollingMovementMethod());
        AsyncTaskTools.execute(new LogCatTask(this));
        //experimental end


        //code to load Google Play API start

        // Create an instance of GoogleAPIClient.
        GoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    //.addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        GoogleApiClient.connect();

        //code to load Google Play API end

    }

    @Override
    public void onConnected(Bundle connectionHint) {

        //on connected is called when Google Play Services API is connected ... used for location stuff
        Log.d("RTL_LOG","Connected to Google Play Services");
        //check permissions start
        int locationPermission= ActivityCompat.checkSelfPermission( this, android.Manifest.permission.ACCESS_FINE_LOCATION );
        if (locationPermission != PackageManager.PERMISSION_GRANTED) {
            //does not have permissions, request permission
            ActivityCompat.requestPermissions( this,
                    PERMISSIONS_LOCATION,
                    REQUEST_LOCATION);
        }
        //check permissions end

        //get me the location
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(GoogleApiClient);
        if (mLastLocation != null) {
            Log.d("RTL_LOG","Printing Location");
            //print location to screen
            EditText latEditText = (EditText) findViewById(R.id.latitude);
            EditText lngEditText = (EditText) findViewById(R.id.longitude);
            latEditText.setText(String.valueOf(mLastLocation.getLatitude()));
            lngEditText.setText(String.valueOf(mLastLocation.getLongitude()));
        }
        else
        {
            Log.d("RTL_LOG","Location cannot be retrieved");
        }
    }

    /* I give up trying to get altitude
    private double getElevationFromGoogleMaps(double longitude, double latitude) {
        double result = Double.NaN;
        HttpURLConnection urlConnection = null;

        String link = "https://maps.googleapis.com/maps/api/elevation/"
                + "xml?locations=" + String.valueOf(latitude)
                + "," + String.valueOf(longitude)
                + "&key=AIzaSyDqvZHKMQ1CWVziGirkXu7f6JbJAdBRz8k"; //this is quans API key, do not go over the free limit


        InputStream inputStream = null;
        try {
            URL url = new URL(link);
            Log.d("RTL_LOG",link);

            urlConnection = (HttpURLConnection) url.openConnection();
            Log.d("RTL_LOG","Connecting to site");
            inputStream = new BufferedInputStream(urlConnection.getInputStream());
            Log.d("RTL_LOG","Getting input stream");
            StringWriter writer = new StringWriter();
            IOUtils.copy(inputStream, writer, "UTF-8");
            String respStr = writer.toString();

            Log.d("RTL_LOG",respStr);
            String tagOpen = "<elevation>";
            String tagClose = "</elevation>";
            if (respStr.indexOf(tagOpen) != -1) {
                int start = respStr.indexOf(tagOpen) + tagOpen.length();
                int end = respStr.indexOf(tagClose);
                String value = respStr.substring(start, end); //elevation value sits between <elevation> tags
                result = (double)(Double.parseDouble(value)); //parse to double
            }
            inputStream.close();
            urlConnection.disconnect();
        }
        catch (Exception e) {
            Log.d("RTL_LOG",inputStream.getErrorStream() );
        }
        return result;
    }
    */

    @Override
    public void onConnectionSuspended(int cause)
    {
        Log.d("RTL_LOG","Connection to Google Play Services suspended");
    }

    private static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    public void RunButtonOnClick (View view) throws ExecutionException, InterruptedException, IOException, ParseException {
        Thread workerThread;

        verifyStoragePermissions(this);

        //Create Working Directory
        if (!dirName.exists()) {
            dirName.mkdirs();
        }

        //define a new runnable class which defines what the worker thread does
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
            // load library already done at the top
            //code to open USB device start
            //enumerate through devices from android i.e. availableUSBDevices does the two lines below
            //UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            //HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
            Set<UsbDevice> availableUsbDevices = UsbPermissionHelper.getAvailableUsbDevices(getApplicationContext(), R.xml.device_filter);

            //when the time comes, replace hardcoded arguments with proper ones
            final String[] argv = {"-f", "88M:108M:125k", dirName + "/" + batchID + ".csv"};

            switch (availableUsbDevices.size()) {
                case 1:
                    UsbDevice usbDevice = availableUsbDevices.iterator().next(); //get me the only usb device in availableUSBDevices
                    Log.d("RTL_LOG","1 USB Device detected: "+ usbDevice.getDeviceName());
                    try {
                        //set up device connection + ask for permissions
                        UsbDeviceConnection deviceConnection = UsbPermissionObtainer.obtainFdFor(getApplicationContext(), usbDevice).get();

                        //print shit to screen if errored out
                        TextView textView = (TextView) findViewById(R.id.textView);
                        if (deviceConnection == null)
                        {
                            textView.append("Could not get a connection to the USB");
                            throw new RuntimeException("Could not get a connection");
                        }

                        //otherwise USB device connection established lovelyyyy
                        int fd = deviceConnection.getFileDescriptor(); //to be passed to c
                        Log.d("RTL_LOG","Opening fd: "+fd);
                        String path = usbDevice.getDeviceName();//to be passed to c
                        Log.d("RTL_LOG","USB path: "+path);
                        passFDandDeviceName(fd,path); //method to pass to c
                    } catch (ExecutionException ee)
                    {
                        Log.d("RTL_LOG", "something fucked up with enumerating the available USB devices. Execution Exception.");
                    }
                    catch (InterruptedException ie)
                    {
                        Log.d("RTL_LOG", "something fucked up with enumerating the available USB devices. Interrupted Exception");
                    }
                    break;
                default:
                    Log.d("RTL_LOG", "something fucked up with enumerating the available USB devices. 0 Devices connected??");
                    return;
            }
            //code to open USB device end
            //call c method with hard coded arguments
            stringFromJNI(argv);
            }
        };

        if (isRunning) //program is already running, lets  stop it
        {
            isRunning = false; //change status of program
            ((Button) findViewById(R.id.button)).setText("Start");
            staphRTLPOWER();// set a global volatile var do_exit in c to quit.

            Log.d("RTL_LOG", "Waiting for rtl_power to terminate. Begin loop...");
            int executionStatus;
            do {
                executionStatus = readExecutionFinished();
                //Implement wait
                //Logging such as Log.d("RTL_LOG", "something fucked up with enumerating the available USB devices. 0 Devices connected??");
            } while (executionStatus == 0);

            //Trigger CsvToJson Async thread
            //HTTP Async thread will be triggered after this thread is complete
            Log.d("RTL_LOG", "rtl_power terminated. Begin conversion...");
            AsyncTaskTools.execute(new CsvConverter(dirName.toString(), batchID, "10s"));
        }
        else //program is not running, lets start it
        {
            isRunning = true;
            batchID = getbatchID(); //Set batch ID to current datetime
            ((Button) findViewById(R.id.button)).setText("Stop");

            //currently unused stuff --begin block
            EditText freqLower = (EditText) findViewById(R.id.freqLower);
            EditText freqHigher = (EditText) findViewById(R.id.freqHigher);
            EditText bins = (EditText) findViewById(R.id.bins);
            EditText fileName = (EditText) findViewById(R.id.fileName);
            TextView textView = (TextView) findViewById(R.id.textView);

            //print arguments to text view
            textView.append("Lower Frequency Bound: " + freqLower.getText().toString() + "\n");
            textView.append("Higher Frequency Bound: " + freqHigher.getText().toString() + "\n");
            textView.append("Number of Bins: " + bins.getText().toString() + "\n");
            textView.append("File Name: " + fileName.getText().toString()+ "\n");

            //put args into string array and send to main
            //currently unused stuff --end block

            //start calling rtl power in another thread
            workerThread = new Thread(runnable);
            workerThread.start();
        }
    }

    private static String getbatchID() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
        Date date = new Date();
        return sdf.format(date);
    }

    public void showDialog(final DialogManager.dialogs id, final String ... args) {
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        GoogleApiClient.disconnect();
        super.onStop();

    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    }

    public void switchToGoogleMaps(View view)
    {
        Intent intent = new Intent(getApplicationContext(), LocationMapActivity.class);
        startActivity(intent);
    }
}
