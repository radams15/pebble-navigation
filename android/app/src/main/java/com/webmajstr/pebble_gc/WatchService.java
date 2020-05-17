package com.webmajstr.pebble_gc;

import java.util.Locale;
import java.util.UUID;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.google.android.gms.location.LocationRequest;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.GeomagneticField;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

public class WatchService extends Service {

	LocationManager locationManager;
	LocationListener locationListener;

	Locale locale = Locale.ENGLISH;

	Location geocacheLocation = new Location("");

	float gc_difficulty, gc_terrain;
	String gc_name, gc_code, gc_size;

	float declination = 1000;

	private static final int DISTANCE_KEY = 0;
	private static final int BEARING_INDEX_KEY = 1;
	private static final int EXTRAS_KEY = 2;
	private static final int DT_RATING_KEY = 3;
	private static final int GC_NAME_KEY = 4;
	private static final int GC_CODE_KEY = 5;
	private static final int GC_SIZE_KEY = 6;
	private static final int AZIMUTH_KEY = 7;
	private static final int DECLINATION_KEY = 8;

	private UUID uuid = UUID.fromString("6191ad65-6cb1-404f-bccc-2446654c20ab"); //v2

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		// Listen when notification is clicked to close the service
		IntentFilter filter = new IntentFilter("android.intent.CLOSE_ACTIVITY");
		registerReceiver(mReceiver, filter);

		//set to run in foreground, so it's not killed by android
		showNotification();


		//register for GPS location updates
		registerLocationUpdates();

		startWatchApp();

	}

	public void stopApp() {
		this.stopSelf();
	}

	BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

			if (intent.getAction().equals("android.intent.CLOSE_ACTIVITY")) {
				stopApp();
			}

		}

	};

	void registerLocationUpdates() {
		// Acquire a reference to the system Location Manager
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		// Define a listener that 'responds' to location updates
		Log.i("GPS", "Initialise");
		locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				Log.i("GPS", "New Location");
				locationUpdate(location);
			}

			public void onStatusChanged(String provider, int status, Bundle extras) {
				Log.i("GPS", "Status Changed To: " + status);
			}

			public void onProviderEnabled(String provider) {
				Log.i("GPS", "Enabled");
			}

			public void onProviderDisabled(String provider) {
				Log.i("GPS", "Disabled");
			}
		};

		// Register the listener with the Location Manager to receive location updates
		// impact on battery has not been determined
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			return;
		}
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1, locationListener);
      	
    }
    
    void locationUpdate(Location currentLocation){

    	if(!currentLocation.getProvider().equals("gps")) return;
    	
    	// calculate declination at this point. This is done only once as it shouldn't change so much at similar location on earth ;)
    	//only run this if first time!
    	if (declination > 999){
    		GeomagneticField geomagneticField = new GeomagneticField((float)currentLocation.getLatitude(),
                (float)currentLocation.getLongitude(), (float)currentLocation.getAltitude(),currentLocation.getTime());
    		declination = geomagneticField.getDeclination();
    	}
    	float distance = currentLocation.distanceTo(geocacheLocation);
    	float deviceBearing = currentLocation.getBearing();
    	float azimuth = currentLocation.bearingTo(geocacheLocation);
    	if(azimuth < 0) azimuth = 360 + azimuth;
    	if(deviceBearing < 0) deviceBearing = 360 + deviceBearing;

    	float bearing = azimuth - deviceBearing;
		if(bearing < 0) bearing = 360 + bearing;
		
		updateWatchWithLocation(distance, bearing, azimuth);
    
    }
    
    public void startWatchApp() {
        PebbleKit.startAppOnPebble(getApplicationContext(), uuid);
    }

    public void stopWatchApp() {
        PebbleKit.closeAppOnPebble(getApplicationContext(), uuid);
    }
    
    
    public void updateWatchWithLocation(float distance, float bearing, float azimuth) {

    	int bearingInt = Math.round(bearing);
    	int distanceInt = Math.round(distance);
    	int azimuthInt = Math.round(azimuth);
    	
    	// convert bearing in degrees to index of image to show. north +- 15 degrees is index 0,
    	// bearing of 30 degrees +- 15 degrees is index 1, etc..
    	int bearingIndex = ((bearingInt + 15)/30) % 12;

        Log.i("Distance", String.format(locale,"%d m", distanceInt));
        Log.i("Bearing", String.valueOf(bearingIndex));
        Log.i("Azimuth", String.valueOf(azimuthInt));
        Log.i("Declination", String.valueOf(Math.round(declination)));
    	    	
    	sendToPebble(String.format(locale,"%d m", distanceInt), bearingIndex, azimuthInt, Math.round(declination) );
    	
    }
    
    public void sendToPebble(String distance, int bearingIndex, int azimuth, int decl) {
    	
    	boolean hasExtras = checkHasExtras();
    	
    	PebbleDictionary data = new PebbleDictionary();

        data.addString(DISTANCE_KEY, distance);
        data.addUint8(BEARING_INDEX_KEY, (byte)bearingIndex);
        data.addUint16(AZIMUTH_KEY, (short) azimuth);
        data.addInt16(DECLINATION_KEY, (short) decl);
        data.addUint8(EXTRAS_KEY, (byte)(hasExtras?1:0) );

        Log.i("Nace", String.valueOf( (short)decl ));
        
        if(hasExtras){
        	
        	data.addString(DT_RATING_KEY, "D"+((gc_difficulty == (int)gc_difficulty) ? String.format(locale, "%d", (int)gc_difficulty) : String.format("%s", gc_difficulty))+" / T"+
        			((gc_terrain == (int)gc_terrain) ? String.format(locale, "%d", (int)gc_terrain) : String.format("%s", gc_terrain)) );
        	
        	data.addString(GC_NAME_KEY, (gc_name.length() > 20) ? gc_name.substring(0, 20) : gc_name);
        	data.addString(GC_CODE_KEY, gc_code);
        	data.addString(GC_SIZE_KEY, gc_size);
        	
        }
        
        PebbleKit.sendDataToPebble(getApplicationContext(), uuid, data);
        
    }
    
    private boolean checkHasExtras() {

    	// function that makes sure that extras exists.
    	// Not really smart way, but works
        return gc_name != null && gc_code != null && gc_size != null;
    	
	}

	private void showNotification() {
    	
        Intent notificationIntent = new Intent("android.intent.CLOSE_ACTIVITY");
        PendingIntent intent = PendingIntent.getBroadcast(this, 0 , notificationIntent, 0);

        NotificationCompat.Builder notBuilder = new NotificationCompat.Builder(getApplicationContext())
    		    .setSmallIcon(R.drawable.ic_launcher)
    		    .setContentTitle(getText(R.string.app_name))
    		    .setContentText(getText(R.string.service_started))
    		    .setContentIntent(intent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String NOTIFICATION_CHANNEL_ID = "com.webmajstr.pebble_gc";
            String channelName = "My Background Service";
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);

            notBuilder.setChannelId(NOTIFICATION_CHANNEL_ID);
            notBuilder
                    .setPriority(NotificationManager.IMPORTANCE_MIN)
                    .setCategory(Notification.CATEGORY_SERVICE);
        }

        Notification notification = notBuilder.build();
        startForeground(R.string.service_started, notification);

    } 
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	
    	double gc_latitude = intent.getDoubleExtra("latitude", 0.0);
    	double gc_longitude = intent.getDoubleExtra("longitude", 0.0);

    	Log.i("Lon & Lat of Target", gc_longitude + " " + gc_latitude);
    	
    	gc_difficulty = intent.getFloatExtra("difficulty", 0);
		gc_terrain = intent.getFloatExtra("terrain", 0);
		gc_name = intent.getStringExtra("name");
		gc_code = intent.getStringExtra("code");
		gc_size = intent.getStringExtra("size");
		
        geocacheLocation.setLatitude( gc_latitude );
        geocacheLocation.setLongitude( gc_longitude );

        //reset watch to default state
        //TODO do make sure the watchapp is listening to messages
    	sendToPebble("GPS Lost", 0, 0, 0);
    	
    	Toast.makeText(this, R.string.navigation_has_started, Toast.LENGTH_LONG).show();
                
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
    	
    	stopWatchApp();
    	
    	unregisterReceiver(mReceiver);
    	
    	// stop listening for GPS updates
		locationManager.removeUpdates(locationListener);
		  
		// stop foreground
		stopForeground(true);

		super.onDestroy();
    }
    
    
}