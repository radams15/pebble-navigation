package com.webmajstr.pebble_gc;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioGroup;

import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    private SharedPreferences prefs;


    final String[] locationPermissions = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };
    final String[] locationPermissions2 = new String[]{
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.FOREGROUND_SERVICE_LOCATION,
    };

    final String[] notificationPermissions = new String[]{
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.POST_NOTIFICATIONS,
    };

    private void requestPermissionsWrapper(String[] permissions, int code, Button btn) {
        boolean requestPermissions = false;

        for(String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions = true;
            }
        }

        if(requestPermissions)
            requestPermissions(permissions, code);

        checkPermissions(permissions, btn);
    }

    private void checkPermissions(String[] permissions, Button btn) {
        boolean requestPermissions = false;

        for(String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions = true;
            }
        }

        if(!requestPermissions)
            btn.setEnabled(false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        setContentView(R.layout.activity_main);

        Button locationBtn = findViewById(R.id.locationBtn);
        Button locationBtn2 = findViewById(R.id.locationBtn2);
        Button notificationsBtn = findViewById(R.id.notificationsBtn);

        checkPermissions(locationPermissions, locationBtn);
        checkPermissions(locationPermissions2, locationBtn2);
        checkPermissions(notificationPermissions, notificationsBtn);

        locationBtn.setOnClickListener(view -> requestPermissionsWrapper(locationPermissions, 1, locationBtn));
        locationBtn2.setOnClickListener(view -> requestPermissionsWrapper(locationPermissions2, 2, locationBtn2));
        notificationsBtn.setOnClickListener(view -> requestPermissionsWrapper(notificationPermissions, 3, notificationsBtn));


        NotificationChannel watchServiceChannel = new NotificationChannel(
                "WatchService",
                "Watch Service",
                NotificationManager.IMPORTANCE_HIGH
        );
        watchServiceChannel.setDescription("Watch Service Channel");

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(watchServiceChannel);


        RadioGroup unitGroup = (RadioGroup) findViewById(R.id.unitGroup);
        Units chosenUnit = UnitOps.convert(prefs.getInt("units", UnitOps.convert(Units.METRIC)));
        if(chosenUnit == Units.IMPERIAL){
            unitGroup.check(R.id.imperialBtn);
        }else if(chosenUnit == Units.MIXED){
            unitGroup.check(R.id.mixedBtn);
        }else{
            unitGroup.check(R.id.metricBtn);
        }

        unitGroup.setOnCheckedChangeListener((RadioGroup group, int checkedBtn) -> {
            if(checkedBtn == R.id.metricBtn){
                onUnitChange(Units.METRIC);
            }else if(checkedBtn == R.id.imperialBtn){
                onUnitChange(Units.IMPERIAL);
            }else if(checkedBtn == R.id.mixedBtn){
                onUnitChange(Units.MIXED);
            }
        });
    }

    private void onUnitChange(Units unit){
        Log.i("Item", String.valueOf(unit));

        SharedPreferences.Editor prefEditor = prefs.edit();
        prefEditor.putInt("units", UnitOps.convert(unit));
        prefEditor.apply();
    }
}