package com.example.recipeapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class Alarm extends BroadcastReceiver {
    private static final String TAG = "Alarm_receiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive");
        Toast.makeText(context, "Your dish should be ready!", Toast.LENGTH_LONG).show();

        // todo fire notification tablet + watch
    }
}
