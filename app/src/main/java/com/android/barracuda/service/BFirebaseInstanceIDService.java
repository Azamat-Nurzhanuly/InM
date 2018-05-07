package com.android.barracuda.service;

import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

public class BFirebaseInstanceIDService extends FirebaseInstanceIdService {
    private static final String TAG = "BFirebaseInstanceIDSer";

    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();

        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(TAG, "New token: " + refreshedToken);
    }
}
