package com.sdrtouch.rtlsdr;

import android.annotation.SuppressLint;
import android.os.AsyncTask;

/**
 * Created by Jackie on 2/12/2016.
 * This class is responsible for executing all the AsyncTask threads on a Thread Pool Executor,
 * ultimately allowing us to run more than one AsyncTask in the application.
 */

class AsyncTaskTools {
    static <P, T extends AsyncTask<P, ?, ?>> void execute(T task) {
        executeThread(task, (P[]) null);
    }

    @SuppressLint("NewApi")
    private static <P, T extends AsyncTask<P, ?, ?>> void executeThread(T task, P... params) {
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
    }
}
