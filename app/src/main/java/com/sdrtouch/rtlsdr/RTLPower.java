package com.sdrtouch.rtlsdr;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.AsyncTask;
import android.util.Log;

import com.sdrtouch.tools.UsbPermissionHelper;
import com.sdrtouch.tools.UsbPermissionObtainer;

import java.util.Set;
import java.util.concurrent.ExecutionException;

import marto.rtl_tcp_andro.R;


public class RTLPower extends AsyncTask<Object, Object, Object> {
    private StreamActivity activityContext;
    private Boolean backgroundProcessingFailed=false;
    private String batchID;


    //Loads the C library
    static {
        System.loadLibrary("rtlSdrAndroid");
    }

    //C methods
    public native void beginRTLPower(String[] argv);
    public native void passFDandDeviceName(int fd_, String path_);
    public native int checkForFailure();

    public RTLPower(StreamActivity activityContext, String BatchID) {
        this.activityContext = activityContext;
        this.batchID = BatchID;
    }

    @Override
    protected Object doInBackground(Object... params) {
        //load library already done at the top

        //enumerate through devices from android i.e. availableUSBDevices does the two lines below
        //UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        //HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Set<UsbDevice> availableUsbDevices = UsbPermissionHelper.getAvailableUsbDevices(activityContext, R.xml.device_filter);

        switch (availableUsbDevices.size()) {
            case 1:
                UsbDevice usbDevice = availableUsbDevices.iterator().next(); //get me the only usb device in availableUSBDevices
                Log.d("RTL_LOG","1 USB Device detected: "+ usbDevice.getDeviceName());
                try {
                    //set up device connection + ask for permissions
                    UsbDeviceConnection deviceConnection = UsbPermissionObtainer.obtainFdFor(activityContext, usbDevice).get();

                    //print shit to screen if errored out
                    if (deviceConnection == null) {
                        Log.d("RTL_LOG","Could not get a connection to the USB");
                        backgroundProcessingFailed=true;
                        break;
                    }
                    //otherwise USB device connection established lovelyyyy, finalise connection by passing
                    //file descriptor and device name
                    int fd = deviceConnection.getFileDescriptor(); //to be passed to c
                    Log.d("RTL_LOG","Opening fd: "+fd);
                    String path = usbDevice.getDeviceName();//to be passed to c
                    Log.d("RTL_LOG","USB path: "+path);
                    passFDandDeviceName(fd,path); //method to pass to c
                    //By Now, RTL SDR is connected, Call C method with hard coded arguments to start recording
                    //when the time comes, replace hardcoded arguments with proper ones
                    // RTL SDR can tune from 24MHz to 1700MHz
                    // From http://www.acma.gov.au/sitecore/content/Home/Industry/Spectrum/Spectrum-projects/700-MHz-band/700-mhz-auction-commences it looks like we should have intervals of 5MHz
                    //String[] argv = new String[]{"-f", "88M:108M:125k", "-1", activityContext.dirName + "/" + batchID + ".csv"};
                    String[] argv = new String[]{"-f", "1000M:1700M:1M", "-1", activityContext.dirName + "/" + batchID + ".csv"};
                    //Log.d("RTL_LOG", "Passing arguments: " + Arrays.toString(argv));
                    beginRTLPower(argv);
                    deviceConnection.close();
                }
                catch (ExecutionException ee) {
                    Log.d("RTL_LOG", "Unable to enumerate the available USB devices. Execution Exception.");
                    backgroundProcessingFailed=true;
                }
                catch (InterruptedException ie) {
                    Log.d("RTL_LOG", "Unable to enumerate the available USB devices. Interrupted Exception");
                    backgroundProcessingFailed=true;
                }
                break;
            default:
                Log.d("RTL_LOG", "Cannot find any connected USB devices");
                backgroundProcessingFailed=true;
                return null;
        }


        return null;
    }

    @Override
    protected void onPostExecute(Object result) {
        //check if the c methods passed
        if (checkForFailure()==1)
        {
            backgroundProcessingFailed=true;
        }

        if (backgroundProcessingFailed) {
            activityContext.recordSpectrumFailed();
        }
        else {
            activityContext.beginCSVConversion();
        }
    }


}
