package com.nwhacksjss.android.nwhacks;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;
import com.nwhacksjss.android.nwhacks.adapters.TweetInfoWindowAdapter;
import com.nwhacksjss.android.nwhacks.services.TweetUpdateService;
import com.nwhacksjss.android.nwhacks.utils.PermissionUtils;
import com.twitter.sdk.android.core.models.Tweet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GoogleMapsActivity extends AppCompatActivity
        implements
        OnMapReadyCallback,
        GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnMyLocationClickListener,
        ActivityCompat.OnRequestPermissionsResultCallback {

    // Google Maps API key:  AIzaSyBFPtuV05B6cukiW2K-BcMTwnQxeXc7FYs

    private static final String TAG = GoogleMapsActivity.class.getSimpleName();

    /**
     * Flag indicating whether a requested permission has been denied after returning in
     * PermissionUtils.onRequestPermissionsResult(int, String[], int[]).
     */
    private boolean permissionDenied = false;

    private GoogleMap map;

    private GoogleMap.InfoWindowAdapter iwa;

    // The receiver used to get tweet updates from LocationUpdatesService
    private GoogleMapsActivity.TweetUpdateReceiver tweetUpdateReceiver;

    // A reference to the service used to get location updates.
    private TweetUpdateService tweetUpdateService = null;

    // Tracks the bound state of the service.
    private boolean isBoundToTweetUpdates = false;

    // Monitors the state of the connection to the service.
    private final ServiceConnection tweetUpdateServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TweetUpdateService.LocalBinder binder = (TweetUpdateService.LocalBinder) service;
            tweetUpdateService = binder.getService();
            isBoundToTweetUpdates = true;
            Log.i(TAG, "Requesting tweet updates from TweetUpdateService.");
            tweetUpdateService.requestTweetUpdates();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            tweetUpdateService = null;
            isBoundToTweetUpdates = false;
        }
    };

    public static HashMap<LatLng, Long> idLookup = new HashMap<>();

    private static List<Tweet> tweets = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // if tweets is empty, likely due to the activity being first initialized, get latest
        // tweet set from update service
        if (tweets.size() == 0 && TweetUpdateService.getTweets() != null) {
            tweets = TweetUpdateService.getTweets();
        }

        setContentView(R.layout.activity_google_maps);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        iwa = new TweetInfoWindowAdapter(GoogleMapsActivity.this);

        tweetUpdateReceiver = new GoogleMapsActivity.TweetUpdateReceiver();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {

        map = googleMap;

        map.setOnMyLocationButtonClickListener(this);
        map.setOnMyLocationClickListener(this);

        map.setInfoWindowAdapter(iwa);

        enableMyLocation();

        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location location = locationManager.getLastKnownLocation(locationManager.getBestProvider(new Criteria(), false));
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lon), 15));
        } catch (SecurityException e) {
            Toast.makeText(getApplicationContext(), "Location access is not enabled.", Toast.LENGTH_SHORT).show();
        }

        if (!PermissionUtils.isLocationPermissionGranted(this)) {
            PermissionUtils.requestPermission(this);
        } else {
            // Bind to the service
            Log.i(TAG, "Binding TweetUpdateService.");
            bindService(new Intent(this, TweetUpdateService.class), tweetUpdateServiceConnection,
                    Context.BIND_AUTO_CREATE);
        }

        plotTweets();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i(TAG, "Registering TweetUpdateReceiver.");
        LocalBroadcastManager.getInstance(this).registerReceiver(tweetUpdateReceiver,
                new IntentFilter(TweetUpdateService.ACTION_BROADCAST));
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "Unregistering TweetUpdateReceiver.");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tweetUpdateReceiver);

        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBoundToTweetUpdates) {
            // TODO - This method was borrowed from Google... figure out what's happening here re: foreground
            // Unbind from the service. This signals to the service that this activity is no longer
            // in the foreground, and the service can respond by promoting itself to a foreground
            // service.
            Log.i(TAG, "Unbinding TweetUpdateService.");
            unbindService(tweetUpdateServiceConnection);
            isBoundToTweetUpdates = false;
        }
    }

    private void plotTweets() {
        map.clear();
        for (Tweet tweet : tweets) {
            if (tweet.coordinates != null || tweet.place != null) {
                Double lat;
                Double lon;
                if (tweet.coordinates != null) {
                    lat = tweet.coordinates.getLatitude();
                    lon = tweet.coordinates.getLongitude();
                } else {
                    lat = tweet.place.boundingBox.coordinates.get(0).get(0).get(1);
                    lon = tweet.place.boundingBox.coordinates.get(0).get(0).get(0);
                }

                LatLng coords = new LatLng(lat, lon);
                map.addMarker(new MarkerOptions().position(coords).icon(BitmapDescriptorFactory.defaultMarker(203)));

                // Store id for future identification of tweet
                idLookup.put(coords, tweet.getId());
            }
        }
    }

    /**
     * Enables the My Location layer if the fine location permission has been granted.
     */
    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(this, PermissionUtils.LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, true);
        } else if (map != null) {
            // Access to the location has been granted to the app.
            map.setMyLocationEnabled(true);
        }
    }

    @Override
    public boolean onMyLocationButtonClick() {
        return false;
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {
        Toast.makeText(this, "Current location:\n(" + location.getLatitude() + ", " + location.getLongitude() + ")", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != PermissionUtils.LOCATION_PERMISSION_REQUEST_CODE) {
            return;
        }

        if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation();
        } else {
            // Display the missing permission error dialog when the fragments resume.
            permissionDenied = true;
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (permissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError();
            permissionDenied = false;
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private void showMissingPermissionError() {
        PermissionUtils.PermissionDeniedDialog
                .newInstance(true).show(getSupportFragmentManager(), "dialog");
    }

    /**
     * Receiver for broadcasts sent by TweetUpdateService.
     */
    private class TweetUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            tweets.clear();
            Gson gson = new Gson();
            ArrayList<String> tweetsAsStrings = intent.getStringArrayListExtra(TweetUpdateService.UPDATED_TWEETS);
            for (String tweetString : tweetsAsStrings) {
                Tweet tweet = gson.fromJson(tweetString, Tweet.class);
                tweets.add(tweet);
            }
            plotTweets();
        }
    }
}
