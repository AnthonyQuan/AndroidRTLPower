package com.sdrtouch.rtlsdr;

import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

/**
 * Created by Anthony on 20/03/2017.
 */

public class FirebaseIDService extends FirebaseInstanceIdService{
    @Override
    public void onTokenRefresh() {
        String refreshToken = FirebaseInstanceId.getInstance().getToken();
        Log.d("RTL_LOG", "Firebase Token: " + refreshToken);
    }

}
