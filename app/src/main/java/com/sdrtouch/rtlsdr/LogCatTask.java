package com.sdrtouch.rtlsdr;

import android.app.Activity;
import android.os.AsyncTask;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

import marto.rtl_tcp_andro.R;

/**
 * Created by Jackie on 2/12/2016.
 */

public class LogCatTask extends AsyncTask<Void, String, Void> {
    private AtomicBoolean run = new AtomicBoolean(true);
    private TextView logView;

    public LogCatTask(Activity myContext)
    {
        logView = (TextView) myContext.findViewById(R.id.textView);
    }

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
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        logView.setText(values[0]);
        // find the amount we need to scroll.  This works by
        // asking the TextView's internal layout for the position
        // of the final line and then subtracting the TextView's height
        final int scrollAmount = logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight();
        // if there is no need to scroll, scrollAmount will be <=0
        if (scrollAmount > 0)
            logView.scrollTo(0, scrollAmount);
        else
            logView.scrollTo(0, 0);
        //super.onProgressUpdate(values);
    }
}
