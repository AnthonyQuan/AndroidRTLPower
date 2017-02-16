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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.location.LocationServices;
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

public class StreamActivity extends FragmentActivity implements ConnectionCallbacks, SensorEventListener, OnTaskCompleted {

    private boolean isRunning = false;
    private String batchID = null;
    private File dirName = new File(Environment.getExternalStorageDirectory() + File.separator + "RTL_POWER");
    private Thread workerThread;
    private Button startStopButton;
    private GoogleApiClient GoogleApiClient;
    private SensorManager sensorManager;
    private Sensor pressureSensor;
    private float altitude = 0;
    private double latitude = 0.000000;
    private double longitude = 0.000000;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    // Location Permissions
    private static final int REQUEST_LOCATION = 2;
    private static String[] PERMISSIONS_LOCATION = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };

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

        //experimental code to update textView with logs start
        TextView logView = (TextView) findViewById(R.id.textView);
        logView.setMovementMethod(new ScrollingMovementMethod());

        //code to load Google Play API start
        // Create an instance of GoogleAPIClient.
        GoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                //.addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        GoogleApiClient.connect();

        //code to load Google Play API end
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

        if (pressureSensor != null) {
            Log.d("RTL_LOG","Pressure sensor found");
        }
        else {
            Log.d("RTL_LOG","No pressure sensor found. Unable to calculate altitude");
        }

        startStopButton = (Button)findViewById(R.id.button);

        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        //Create Working Directory
        if (!dirName.exists()) {
            dirName.mkdirs();
        }

        AsyncTaskTools.execute(new LogCatTask(this));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //this method gets called automatically under the hood when the sensor's accuracy change
        //don't need to do anything for our implementation
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        //this method gets called automatically under the hood when the sensor's values change
        float pressure = event.values[0];
        Log.d("RTL_LOG","pressure reading: " + pressure); //leave commented otherwise debug log will get flooded
        altitude = sensorManager.getAltitude(sensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure); //returns altitude in meters
        Log.d("RTL_LOG","altitude: " + altitude); //leave commented otherwise debug log will get flooded

        //I have a reading, stop getting more readings
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        //on connected is called when Google Play Services API is connected, used for location stuff
        Log.d("RTL_LOG","Connected to Google Play Services");

        // Check if we have location permissions
        int fineLocationPermission = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION);
        int coarseLocationPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (fineLocationPermission != PackageManager.PERMISSION_GRANTED || coarseLocationPermission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_LOCATION,
                    REQUEST_LOCATION);
        }

        //get me the last location
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(GoogleApiClient);
        if (mLastLocation != null) {
            latitude = mLastLocation.getLatitude();
            Log.d("RTL_LOG","latitude: " + latitude);
            longitude = mLastLocation.getLongitude();
            Log.d("RTL_LOG","longitude: " + longitude);
        }
        else {
            Log.d("RTL_LOG","Location cannot be retrieved");
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d("RTL_LOG","Connection to Google Play Services suspended");
    }

    public void runButtonOnClick(View view) throws ExecutionException, InterruptedException, IOException, ParseException {
        //define a new runnable class which defines what the worker thread does
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
            //load library already done at the top
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
                        if (deviceConnection == null) {
                            textView.append("Could not get a connection to the USB");
                            throw new RuntimeException("Could not get a connection");
                        }

                        //otherwise USB device connection established lovelyyyy
                        int fd = deviceConnection.getFileDescriptor(); //to be passed to c
                        Log.d("RTL_LOG","Opening fd: "+fd);
                        String path = usbDevice.getDeviceName();//to be passed to c
                        Log.d("RTL_LOG","USB path: "+path);
                        passFDandDeviceName(fd,path); //method to pass to c
                    }
                    catch (ExecutionException ee) {
                        Log.d("RTL_LOG", "something fucked up with enumerating the available USB devices. Execution Exception.");
                    }
                    catch (InterruptedException ie) {
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

        if (isRunning) { //program is already running, lets stop it
            isRunning = false; //change status of program
            startStopButton.setText("Start");
            staphRTLPOWER();// set a global volatile var do_exit in c to quit.

            Log.d("RTL_LOG", "Waiting for rtl_power to terminate. Begin loop...");
            int executionStatus;
            do {
                executionStatus = readExecutionFinished();
            } while (executionStatus == 0);

            //Trigger CsvToJson Async thread
            Log.d("RTL_LOG", "rtl_power terminated. Begin conversion...");
            AsyncTaskTools.execute(new CsvConverter(dirName.toString(), batchID, altitude, latitude, longitude, "10s"));
        }
        else { //program is not running, lets start it
            startStopButton.setText("Stop");
            startStopButton.setClickable(false);

            //registering the listener to the sensor, will get me readings from the sensor
            //the onAccuracyChanged method or onSensorChanged method will be called when the sensor values change, in those methods i can access sensor values
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);

            //start calling rtl power in another thread
            workerThread = new Thread(runnable);

            AsyncTaskTools.execute(new SyncTime(StreamActivity.this));
        }
    }

    @Override
    public void onTaskCompleted() {
        Log.d("RTL_LOG", "Device synchronised. Starting rtl_power execution...");
        batchID = getBatchID(); //Set batch ID to current datetime
        workerThread.start();
        isRunning = true;
        startStopButton.setClickable(true);
    }

    private static String getBatchID() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
        Date date = new Date();
        return sdf.format(date);
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
