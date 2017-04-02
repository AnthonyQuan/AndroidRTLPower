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
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import marto.rtl_tcp_andro.R;

public class StreamActivity
        extends
            AppCompatActivity
        implements
            ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            SensorEventListener,
            GoogleMap.OnMyLocationButtonClickListener,
            OnMapReadyCallback,
            ActivityCompat.OnRequestPermissionsResultCallback {
    /*===================================================
     * Global Variables START
     *===================================================*/

    //General UI variable(s)
    private Button RunNowButton;
    private TextView StatusTextGPS;
    private TextView StatusTextAltitude;
    private TextView StatusTextRecord;
    private TextView StatusTextUpload;
    private CheckBox CheckboxGPS;
    private CheckBox CheckboxAltitude;
    private CheckBox CheckboxRecord;
    private CheckBox CheckboxUpload;

    //Run button status variable(s)
    public boolean isRunning = false;

    //Variables required for recording the spectrum
    private String batchID = null;
    public File dirName = new File(Environment.getExternalStorageDirectory() + File.separator + "RTL_POWER");

    //Google Play Services GPS variable(s)
    private GoogleApiClient GoogleApiClient;
    private double latitude = 0;
    private double longitude = 0;

    //Google Maps variable(s)
    private GoogleMap mMap;
    private static final LatLng SYDNEY = new LatLng(-33.8683615,151.2103255);

    //Altitude Sensor variable(s)
    private SensorManager sensorManager;
    private Sensor pressureSensor;
    private float altitude = 0;

    //Storage Permissions
    private static final int WRITE_PERMISSION_REQUEST_CODE = 3;

    //Location Permissions
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int REQUEST_LOCATION = 2;
    private static String[] PERMISSIONS_LOCATION = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };

    //Loads the C library
    static {
        System.loadLibrary("rtlSdrAndroid");
    }

    //C methods
    public native void staphRTLPOWER();
    public native void resetRTLPOWER();
    /*===================================================
     * Global Variables END
     *===================================================*/

    /*===================================================
     * Main Entry Point to App START
     *===================================================*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);

        //Show Google Maps Fragment on app start up
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //Initialise global variables
        RunNowButton = (Button)findViewById(R.id.ButtonRun);
        StatusTextGPS = (TextView) findViewById(R.id.textViewGPS);
        StatusTextAltitude = (TextView) findViewById(R.id.textViewAltitude);
        StatusTextRecord = (TextView) findViewById(R.id.textViewRecord);
        StatusTextUpload = (TextView) findViewById(R.id.textViewUpload);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        CheckboxGPS = (CheckBox) findViewById(R.id.checkBoxGPS);
        CheckboxAltitude = (CheckBox) findViewById(R.id.checkBoxAltitude);
        CheckboxRecord = (CheckBox) findViewById(R.id.checkBoxRecord);
        CheckboxUpload = (CheckBox) findViewById(R.id.checkBoxUpload);
        GoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        //Setup Debug Log to automatically update
        TextView debugLog = (TextView) findViewById(R.id.textView);
        debugLog.setMovementMethod(new ScrollingMovementMethod());
        AsyncTaskTools.execute(new LogCatTask(this));
    }
    /*===================================================
     * Main Entry Point to App END
     *===================================================*/

    /*===================================================
     * Other Entry and Exit Points to App START
     *===================================================*/
    @Override
    protected void onStart() {
        super.onStart();
        GoogleApiClient.connect();
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
        if (GoogleApiClient.isConnected())
            GoogleApiClient.disconnect();
        super.onStop();
    }
    /*===================================================
     * Other Entry and Exit Points to App END
     *===================================================*/

    /*===================================================
     * Altitude Sensor Functions START
     *===================================================*/
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
        //Update status text
        StatusTextAltitude.setText("DONE");
        CheckboxAltitude.setChecked(true);
    }
    /*===================================================
     * Altitude Sensor Functions END
     *===================================================*/

    /*===================================================
     * Google Play Services GPS Functions START
     *===================================================*/
    @Override
    public void onConnected(Bundle connectionHint) {
        //on connected is automatically called when Google Play Services API is connected
        Log.d("RTL_LOG","Connected to Google Play Services");
        getGPSLocation();
        if (latitude != 0 && longitude != 0)
            AsyncTaskTools.execute(new PostToken(latitude, longitude));
        else
            Log.d("RTL_LOG","Location not valid!");
    }

    private void getGPSLocation() {
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
        if (isRunning) {
            Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(GoogleApiClient);
            if (mLastLocation != null) {
                //set global vars (for easier retrieval later)
                latitude = mLastLocation.getLatitude();
                Log.d("RTL_LOG", "latitude: " + latitude);
                longitude = mLastLocation.getLongitude();
                Log.d("RTL_LOG", "longitude: " + longitude);
                //Update status text
                StatusTextGPS.setText("DONE");
                CheckboxGPS.setChecked(true);
            } else {
                Log.d("RTL_LOG", "Location cannot be retrieved");
                //Update status text
                StatusTextGPS.setText("FAILED");
                isRunning = false;
                RunNowButton.setText("RUN NOW");
            }
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        //on connection failed is automatically called when Google Play Services API cannot be connected
        Log.d("RTL_LOG","Cannot Connect to Google Play Services");
        //Update status text
        StatusTextGPS.setText("FAILED");
        isRunning = false;
        RunNowButton.setText("RUN NOW");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d("RTL_LOG","Connection to Google Play Services suspended");
        //Update status text
        StatusTextGPS.setText("FAILED");
        isRunning = false;
        RunNowButton.setText("RUN NOW");
    }
    /*===================================================
     * Google Play Services GPS Functions END
     *===================================================*/

    /*===================================================
     * Button: Run Now START
     *===================================================*/
    public void OnClickRunNow(View view) {
        //ensure GPS and Internet is on before proceeding
        if (gpsIsEnabled() && internetIsEnabled()) {
            // Check if we have write permission
            int writePermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (writePermission == PackageManager.PERMISSION_GRANTED) {
                if (!isRunning) { //program is not running, lets start it
                    Log.d("RTL_LOG","Write access permission granted");
                    isRunning = true;
                    runProgram();
                } else { //program is already running, lets stop it
                    isRunning = false;
                    stopProgram();
                }
            }
            else
                createStorageDirectory();
        }
        else {
            //GPS and/or Internet connection is not enabled, show a dialog box
            DialogGPSInternet dialog = new DialogGPSInternet();
            dialog.show(getSupportFragmentManager(), "gpsInternetDialog");
        }
    }

    public boolean gpsIsEnabled() {
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        //check if GPS is on
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    public boolean internetIsEnabled() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        //check if internet is on or is connecting
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }

    private void createStorageDirectory() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to write to external storage is missing.
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    WRITE_PERMISSION_REQUEST_CODE);
            //PermissionUtils.requestPermission(this, WRITE_PERMISSION_REQUEST_CODE, Manifest.permission.WRITE_EXTERNAL_STORAGE, true);
        } else {
            //Create Working Directory
            if (!dirName.exists())
                dirName.mkdirs();
            Log.d("RTL_LOG","External storage directory established");
        }
    }

    private void runProgram() {
        /*===================================================
         * SetUp START
         *===================================================*/
        //Update Button Text
        RunNowButton.setText("Stop");
        //reset GUI
        StatusTextAltitude.setText("");
        CheckboxAltitude.setChecked(false);
        StatusTextGPS.setText("");
        CheckboxGPS.setChecked(false);
        StatusTextRecord.setText("");
        CheckboxRecord.setChecked(false);
        StatusTextUpload.setText("");
        CheckboxUpload.setChecked(false);
        Log.d("RTL_LOG","Run button pressed");
        // reset RTL power flag(s)
        resetRTLPOWER();
        /*===================================================
         * SetUp END
         *===================================================*/
        /*===================================================
         * Get Altitude START
         *===================================================*/
        //Update GUI with Altitude status
        StatusTextAltitude.setText("RUNNING");
        //check for pressure sensor
        if (pressureSensor !=  null) {
            Log.d("RTL_LOG","Pressure sensor found");
            //Register a listener to the sensor, will get me readings from the sensor
            //the onAccuracyChanged method or onSensorChanged methods will be called when the sensor values change
            //in those methods I can access sensor values
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        else {
            Log.d("RTL_LOG","No pressure sensor found. Unable to calculate altitude");
            StatusTextAltitude.setText("NO PRESSURE SENSOR");
            //altitude will be recorded as zero
        }
        /*===================================================
         * Get Altitude END
         *===================================================*/
        /*===================================================
         * Get GPS START
         *===================================================*/
        //Update GUI with GPS status
        StatusTextGPS.setText("RUNNING");
        //Once the connect method is called below, the listeners
        //onConnected, onConnectionFailed and onConnectionSuspended will be registered
        //Those methods will allow me to get the Lat and Lng coordinates
        if (GoogleApiClient.isConnected()) {
            getGPSLocation(); //already connected to google play services (e.g. from second scan)
            if (latitude != 0 && longitude != 0)
                AsyncTaskTools.execute(new PostToken(latitude, longitude));
            else
                Log.d("RTL_LOG","Location not valid!");
        }
        else
            GoogleApiClient.connect();

        /*===================================================
         * Get GPS END
         *===================================================*/
        /*===================================================
         * Get Sync Time then begin SpectrumRecording START
         *===================================================*/
        StatusTextRecord.setText("SYNCHRONISING");
        AsyncTaskTools.execute(new GetSyncTime(StreamActivity.this));
        //When GetSyncTime finishes, the app continues execution at method beginSpectrumRecording()
        /*===================================================
         * Get Sync Time then begin Spectrum Recording END
         *===================================================*/
        /*===================================================
         * Upload to MongoDB START
         *===================================================*/
        //refer to method beginSpectrumUpload(), which gets called once beginSpectrumRecording() is done
        /*===================================================
         * Upload to MongoDB END
         *===================================================*/
    }

    private void stopProgram() {
        RunNowButton.setText("Stopping");
        RunNowButton.setClickable(false);
        /*===================================================
         * Stop Altitude START
         *===================================================*/
        altitude = 0;
        StatusTextAltitude.setText("");
        CheckboxAltitude.setChecked(false);
        /*===================================================
         * Stop Altitude END
         *===================================================*/
        /*===================================================
         * Stop GPS START
         *===================================================*/
        latitude = 0; //reset values
        longitude = 0;
        if (GoogleApiClient.isConnected())
            GoogleApiClient.disconnect();
        StatusTextGPS.setText("");
        CheckboxGPS.setChecked(false);
        /*===================================================
         * Stop GPS END
         *===================================================*/
        /*===================================================
         * Stop Sync Time START
         *===================================================*/
        //this should periodically check the public boolean isRunning and cancel accordingly
        /*===================================================
         * Stop Sync Time END
         *===================================================*/
        /*===================================================
         * Stop Record START
         *===================================================*/
        staphRTLPOWER();// set a global volatile var do_exit in c to quit.
        /*
        Log.d("RTL_LOG", "Waiting for rtl_power to terminate. Begin loop..");
        int executionStatus;
        do {
        executionStatus = readExecutionFinished();
        } while (executionStatus == 0);
        */
        StatusTextRecord.setText("");
        CheckboxRecord.setChecked(false);
        /*===================================================
         * Stop Record END
         *===================================================*/
        /*===================================================
         * Stop Upload START
         *===================================================*/
        //this should periodically check the public boolean isRunning and cancel accordingly
        StatusTextUpload.setText("");
        CheckboxUpload.setChecked(false);
        /*===================================================
         * Stop Upload END
         *===================================================*/
        //Finished stopping the program
        RunNowButton.setText("RUN NOW");
        RunNowButton.setClickable(true);
    }
    /*===================================================
     * Button: Run Now END
     *===================================================*/

    /*===================================================
     * Continue Execution here after calling AsyncTaskTools.execute(new GetSyncTime(StreamActivity.this)); START
     *===================================================*/
    public void beginSpectrumRecording() {
        if (isRunning) {
            Log.d("RTL_LOG", "Device synchronised. Starting rtl_power execution..");
            //Update GUI with Record status
            StatusTextRecord.setText("RUNNING");
            //start calling rtl power in another thread
            batchID = getBatchID(); //Set batch ID to current datetime
            AsyncTaskTools.execute(new RTLPower(StreamActivity.this, batchID));
        }
        else
            stopSpectrumRecording();
    }

    public void stopSpectrumRecording() {
        Log.d("RTL_LOG", "Stopped Spectrum Recording");
        StatusTextRecord.setText("");
        CheckboxRecord.setChecked(false);
        isRunning = false;
    }

    private static String getBatchID() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
        Date date = new Date();
        return sdf.format(date);
    }
    /*===================================================
     * Continue Execution here after calling AsyncTaskTools.execute(new GetSyncTime(StreamActivity.this)); END
     *===================================================*/

    /*===================================================
     * Continue Execution here after calling AsyncTaskTools.execute(new RTLPower(StreamActivity.this, batchID)); START
     *===================================================*/
    public void beginCSVConversion() {
        if (isRunning) {
            StatusTextRecord.setText("DONE");
            CheckboxRecord.setChecked(true);
            StatusTextUpload.setText("RUNNING");
            AsyncTaskTools.execute(new CsvConverter(StreamActivity.this, dirName.toString(), batchID, altitude, latitude, longitude, "10s"));
        }
        else
            stopSpectrumRecording();
    }

    public void recordSpectrumFailed() {
        Log.d("RTL_LOG", "Spectrum Recording Failed");
        StatusTextRecord.setText("FAILED");
        isRunning = false;
        RunNowButton.setText("RUN NOW");
    }

    public void stopSpectrumUpload() {
        Log.d("RTL_LOG", "Stopped Spectrum Upload");
        StatusTextUpload.setText("");
        CheckboxUpload.setChecked(false);
        isRunning = false;
    }
    /*===================================================
     * Continue Execution here after calling AsyncTaskTools.execute(new RTLPower(StreamActivity.this, batchID)); END
     *===================================================*/

    /*===================================================
     * Continue Execution here after calling AsyncTaskTools.execute(new CsvConverter START
     *===================================================*/
    public void beginUploadtoMongoDB() {
        if (isRunning)
            AsyncTaskTools.execute(new PostSpectrum(StreamActivity.this, dirName.toString(), batchID));
        else
            stopSpectrumUpload();
    }

    public void csvConversionFailed() {
        Log.d("RTL_LOG", "CSV Conversion Failed");
        StatusTextUpload.setText("FAILED");
        isRunning = false;
        RunNowButton.setText("RUN NOW");
    }
    /*===================================================
     * Continue Execution here after calling AsyncTaskTools.execute(new CsvConverter END
     *===================================================*/

    /*===================================================
     * Continue Execution here after calling AsyncTaskTools.execute(new PostSpectrum START
     *===================================================*/
    public void uploadSucessful() {
        if (isRunning) {
            CheckboxUpload.setChecked(true);
            StatusTextUpload.setText("DONE");
            isRunning = false;
            RunNowButton.setText("RUN NOW");
        }
        else
            stopSpectrumUpload();
    }

    public void uploadFailed() {
        Log.d("RTL_LOG", "HTTP Post Failed");
        StatusTextUpload.setText("FAILED");
        isRunning = false;
        RunNowButton.setText("RUN NOW");
    }
    /*===================================================
     * Continue Execution here after calling AsyncTaskTools.execute(new PostSpectrum END
     *===================================================*/

    /*===================================================
     * Button: toggle between Google maps and debug log START
     *===================================================*/
    public void OnClickToggleGoogleMapsAndDebugLog(View view) {
        //local variables
        TextView debugLog = (TextView) findViewById(R.id.textView);
        Button toggleButton = (Button)findViewById(R.id.ButtonToggleGoogleMapsAndDebugLog);
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        //toggle between Google Maps and Debug Log
        //by default, the debug log should be "gone" and google maps is visible
        if (debugLog.getVisibility() == View.GONE) {
            //Change label of button
            toggleButton.setText("View Location on Google Maps");
            //Hide Google Maps
            fragmentTransaction.hide(getSupportFragmentManager().findFragmentById(R.id.map));
            fragmentTransaction.commit();
            //Show debug log
            debugLog.setVisibility(View.VISIBLE);
        } else {
            //Change label of button
            toggleButton.setText("View Detailed Debug Info");
            //Hide debug log
            debugLog.setVisibility(View.GONE);
            //Show Google Maps
            fragmentTransaction.show(getSupportFragmentManager().findFragmentById(R.id.map));
            fragmentTransaction.commit();
        }
    }
    /*===================================================
     * Button: toggle between Google maps and debug log End
     *===================================================*/

    /*===================================================
     * Google Maps Fragment Start
     *===================================================*/
    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
        mMap.setOnMyLocationButtonClickListener(this);
        enableMyLocation();
        //enable 3d buildings,
        map.setBuildingsEnabled(true);

        // Construct a CameraPosition focusing on Sydney and animate the camera to that position.
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(SYDNEY)             // Sets the center of the map to Sydney CBD
                .zoom(16)                   // Sets the zoom
                .bearing(0)                 // Sets the orientation of the camera to east
                .tilt(85)                   // Sets the tilt of the camera to 30 degrees
                .build();                   // Creates a CameraPosition from the builder
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    //Enables the My Location layer if the fine location permission has been granted.
    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(this, LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, true);
        } else if (mMap != null) {
            // Access to the location has been granted to the app.
            mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    public boolean onMyLocationButtonClick() {
        Toast.makeText(this, "Moving To My Location", Toast.LENGTH_SHORT).show();
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE: {
                if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                        Manifest.permission.ACCESS_FINE_LOCATION)) {
                    // Enable the my location layer if the permission has been granted.
                    enableMyLocation();
                }
            }
            case WRITE_PERMISSION_REQUEST_CODE: {
                if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    // Create the storage directory if the permission has been granted.
                    createStorageDirectory();
                }
            }
        }
    }
    /*===================================================
     * Google Maps Fragment End
     *===================================================*/
}
