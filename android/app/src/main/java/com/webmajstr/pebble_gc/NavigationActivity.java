package com.webmajstr.pebble_gc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import android.app.Activity;
import android.content.Intent;

import java.util.Objects;

import geo.gps.Coordinates;

public class NavigationActivity extends Activity {

    // Variables that I pass on to Service
    double gc_longitude, gc_latitude;
    float gc_difficulty, gc_terrain;
    String gc_name, gc_code, gc_size;
    Units gc_units = Units.METRIC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();

        if(intent == null) finish();

        /*
         * Parse intent from c:geo app
         */
        assert intent != null;
        switch (Objects.requireNonNull(intent.getAction())) {
            case "com.webmajstr.pebble_gc.NAVIGATE_TO":

                gc_latitude = intent.getDoubleExtra("latitude", 0.0);
                gc_longitude = intent.getDoubleExtra("longitude", 0.0);
                gc_difficulty = intent.getFloatExtra("difficulty", 0);
                gc_terrain = intent.getFloatExtra("terrain", 0);

                gc_name = intent.getStringExtra("name");
                gc_code = intent.getStringExtra("code");
                gc_size = intent.getStringExtra("size");

                startWatchService();
                finish();

                /*
                 * Parse intent from Locus app. This should be expanded to support more apps. But are there any standards? :)
                 * Google Maps are not supported as it doesn't provide coordinates :/
                 */
                break;
            case Intent.ACTION_SEND:

                String text = intent.getStringExtra("android.intent.extra.TEXT");
                if (text != null) {

                    text = text.replace("°", " ");
                    text = text.replace("'", " ");

                    Coordinates gps = new Coordinates();

                    // Locus app has new format for coordinates.. It says "Point, " at begining of data... :/
                    // This is a dirty hack..
                    String text2 = text.substring(text.indexOf(",") + 1);

                    if (gps.parse(text) || (gps.parse(text2))) {

                        gc_latitude = gps.getLatitude();
                        gc_longitude = gps.getLongitude();

                        gc_name = gc_code = gc_size = null;

                        startWatchService();
                        finish();

                    } else {
                        Toast.makeText(getApplicationContext(), getText(R.string.error_message) + " #13", Toast.LENGTH_SHORT).show();
                    }

                } else {
                    Toast.makeText(getApplicationContext(), getText(R.string.error_message) + " #14", Toast.LENGTH_SHORT).show();
                }


                /*
                 * Parse intent from basic Radar request. Works for example with official GeoCaching app.
                 */
                break;
            case "com.google.android.radar.SHOW_RADAR":

                gc_latitude = intent.getFloatExtra("latitude", 0.0f);
                gc_longitude = intent.getFloatExtra("longitude", 0.0f);

                gc_name = gc_code = gc_size = null;

                startWatchService();
                finish();

                /*
                 * Not yet implemented.
                 */
                break;
            case Intent.ACTION_VIEW:

                break;
        }

    }

    /**
     * Starts service that communicates with Pebble and passes all needed data
     */
    public void startWatchService() {
        Intent intent = new Intent(NavigationActivity.this, WatchService.class);
        intent.putExtra("latitude", gc_latitude);
        intent.putExtra("longitude", gc_longitude);

        intent.putExtra("difficulty", gc_difficulty);
        intent.putExtra("terrain", gc_terrain);
        intent.putExtra("name", gc_name);
        intent.putExtra("code", gc_code);
        intent.putExtra("size", gc_size);

        SharedPreferences prefs = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        gc_units = UnitOps.convert(prefs.getInt("units", UnitOps.convert(Units.METRIC)));

        System.out.println("Using Units: " + gc_units);

        intent.putExtra("units", gc_units == null? Units.METRIC : gc_units);

        startService(intent);
    }

}
