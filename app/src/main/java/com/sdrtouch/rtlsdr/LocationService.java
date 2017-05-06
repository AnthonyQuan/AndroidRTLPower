package com.sdrtouch.rtlsdr;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;


public class LocationService extends Service implements com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks {
    //This service is designed to send gps location + firebase token + timestamp to the server every 3 mins to track last user location for notifications
    //the location data here is separate to the gps location tied with the spectrum data

    //Google Play Services GPS variable(s)
    private GoogleApiClient googleApiClient;
    private double latitude = 0;
    private double longitude = 0;

    //Location Permissions
    private static String[] PERMISSIONS_LOCATION = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };


    @Override
    public IBinder onBind(Intent intent) {
        return null;
        //not used but is required to be overriden
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("RTL_LOG","Location Service started");

        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .build();
        googleApiClient.connect();
        return START_STICKY;
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        String android_id = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID); // get me the device android ID, pass this to the worker thread

        //since a service runs in the main UI thread, we run the main computational loop on another thread
        new AsyncTask<String, String, Void>() {
            @Override
            protected Void doInBackground(String... params) {
                String android_id_aysncTask = params[0]; //get the android ID passed to this async task worker thread
                while(true) { //run this indefinitely
                    if (googleApiClient.isConnected() && gpsIsEnabled()) {
                        Log.d("RTL_LOG", "Location Service: Sending periodic location update");
                        getGPSLocation(); //get me the GPS coordinates
                        AsyncTaskTools.execute(new PostToken(android_id_aysncTask, latitude, longitude)); //this will send the android ID, gps coord alongside the firebase token and timestamp
                        try {
                            Thread.sleep(1000 * 60 * 3); //1000 (1000 millisecond in a second) * 60 (60 seconds in a minute) * 3 (we want to wait 3 mins)
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, android_id);
    }

    @Override
    public void onConnectionSuspended(int i) {
        //not used but is necessary to have
    }

    private void getGPSLocation() {
        // Check if we have location permissions
        int fineLocationPermission = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION);
        int coarseLocationPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (fineLocationPermission == PackageManager.PERMISSION_GRANTED || coarseLocationPermission == PackageManager.PERMISSION_GRANTED) {
            //get me the last location
            Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            if (mLastLocation != null) {
                //set global vars (for easier retrieval later)
                latitude = mLastLocation.getLatitude();
                longitude = mLastLocation.getLongitude();
            }
        }
    }

    public boolean gpsIsEnabled() {
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        //check if GPS is on
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    @Override
    public void onDestroy() {
        Log.d("RTL_LOG", "Location Service destroyed");
        if (googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
        super.onDestroy();
    }
}
