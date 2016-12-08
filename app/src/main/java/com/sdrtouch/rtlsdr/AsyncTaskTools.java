package com.sdrtouch.rtlsdr;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;

/**
 * Created by Jackie on 2/12/2016.
 * This class is responsible for executing all the AsyncTask threads on a Thread Pool Executor,
 * ultimately allowing us to run more than one AsyncTask in the application.
 */

public class AsyncTaskTools {
    public static <P, T extends AsyncTask<P, ?, ?>> void execute(T task) {
        executeThread(task, (P[]) null);
    }

    @SuppressLint("NewApi")
    private static <P, T extends AsyncTask<P, ?, ?>> void executeThread(T task, P... params) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        } else {
            task.execute(params);
        }
    }
}
