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

import java.io.*;
import android.os.AsyncTask;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.os.Environment;
import com.sdrtouch.core.exceptions.SdrException.err_info;
import com.sdrtouch.rtlsdr.BinaryRunnerService.LocalBinder;
import com.sdrtouch.tools.Check;
import com.sdrtouch.tools.DialogManager;
import com.sdrtouch.tools.DialogManager.dialogs;
import com.sdrtouch.tools.Log;
import java.util.concurrent.atomic.AtomicBoolean;
import android.text.method.ScrollingMovementMethod;

import marto.rtl_tcp_andro.R;

public class StreamActivity extends FragmentActivity implements Log.Callback {

	boolean isRunning=false;
    private static final int START_REQ_CODE = 1;
    private BinaryRunnerService service;



    // loads the c library
    static {
        System.loadLibrary("rtlSdrAndroid");
    }

    //c method
    public native String stringFromJNI(String[] argv);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_stream);
        //experimental start
        TextView textView = (TextView) findViewById(R.id.textView);
        textView.setMovementMethod(new ScrollingMovementMethod());
        LogCatTask logCatTask = new LogCatTask(){
            @Override
            protected void onProgressUpdate(String... values) {
                TextView textView = (TextView) findViewById(R.id.textView);
                textView.setText(values[0]);
                super.onProgressUpdate(values);
            }
        };
        logCatTask.execute();
        //experimental end


	}

    public class LogCatTask extends AsyncTask<Void, String, Void> {
        public AtomicBoolean run = new AtomicBoolean(true);

        @Override
        protected Void doInBackground(Void... params) {
            try {
                Runtime.getRuntime().exec("logcat -c");
                Process process = Runtime.getRuntime().exec("logcat RTL_LOG:V *:S -v raw");
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder log = new StringBuilder();
                String line = "";
                while (run.get()) {
                    line = bufferedReader.readLine();
                    if (line != null) {
                        log.append(line + "\n");
                        publishProgress(log.toString());
                    }
                    line = null;
                    Thread.sleep(10);
                }
            }
            catch(Exception ex){

            }
            return null;
        }
    }

	public void RunButtonOnClick(View view)
    {
        Thread workerThread=null;
        final String[] argv = {"-f", "88M:108M:125k", Environment.getExternalStorageDirectory()+"/filename.csv"};

        //define a new runnable class which defines what the worker thread does
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // load library already done at the top

                //call c method
                stringFromJNI(argv);
            }
        };


        if (isRunning==true) //program is already running, lets  stop it
        {
            isRunning=false; //change status of program
            ((Button) findViewById(R.id.button)).setText("Start");
            workerThread.interrupt();
        }
        else //program is not running, lets start it
        {
            isRunning=true;
            ((Button) findViewById(R.id.button)).setText("Stop");
            //start background thread to run RTL Power
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

            //put my args into a string array


            //start calling rtl power in another thread
            workerThread = new Thread(runnable);
            workerThread.start();
        }
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
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {

	}
	
	@Override
	public void onChanged() {
	}
}
