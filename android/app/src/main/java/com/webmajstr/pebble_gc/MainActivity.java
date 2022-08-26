package com.webmajstr.pebble_gc;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.RadioGroup;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    private SharedPreferences prefs;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 1);
            }
        }

        setContentView(R.layout.activity_main);

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