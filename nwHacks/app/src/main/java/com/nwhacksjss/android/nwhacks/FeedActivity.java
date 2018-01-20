package com.nwhacksjss.android.nwhacks;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.maps.model.LatLng;
import com.nwhacksjss.android.nwhacks.services.LocationUpdateService;
import com.nwhacksjss.android.nwhacks.utils.PermissionUtils;
import com.twitter.sdk.android.core.TwitterApiClient;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.models.Search;
import com.twitter.sdk.android.core.models.Tweet;
import com.twitter.sdk.android.core.services.SearchService;
import com.twitter.sdk.android.core.services.params.Geocode;
import com.twitter.sdk.android.tweetui.TweetView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FeedActivity extends AppCompatActivity {

    private static final String TAG = FeedActivity.class.getSimpleName();

    // The receiver used to get location updates from LocationUpdatesService
    private LocationUpdateReceiver locationUpdateReceiver;

    // A reference to the service used to get location updates.
    private LocationUpdateService locationUpdateService = null;

    // Tracks the bound state of the service.
    private boolean isBoundToLocationUpdates = false;

    // Monitors the state of the connection to the service.
    private final ServiceConnection locationServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationUpdateService.LocalBinder binder = (LocationUpdateService.LocalBinder) service;
            locationUpdateService = binder.getService();
            isBoundToLocationUpdates = true;
            Log.i(TAG, "Requesting location updates from LocationUpdateService.");
            locationUpdateService.requestLocationUpdates();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            locationUpdateService = null;
            isBoundToLocationUpdates = false;
        }
    };

    // UI elements
    private View view;
    private View contentFeed;
    private LinearLayout linearLayout;
    private ProgressBar progressBar;

    private HashMap<Long, LatLng> tweetIdCoordinates= new HashMap<>();
    private List<Tweet> tweets;
    private ArrayList<LatLng> tweetCoords;
    private Long lastSinceId;
    private static Location loc;
    private static Geocode currentLocation;

    private static boolean firstRun = true; // TODO - this is for debugging...remove eventually

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed);

        view = findViewById(R.id.activity_feed);
        contentFeed = findViewById(R.id.content_feed);
        linearLayout = findViewById(R.id.feed_layout);
        progressBar = findViewById(R.id.progress_bar_content_feed);

        PermissionUtils.fullRequestPermissionProcess(this, view);

        locationUpdateReceiver = new LocationUpdateReceiver();

        tweetCoords = new ArrayList<>();
        tweets = new ArrayList<>();
        lastSinceId = 951701941301624832l; // TODO: generate new sinceId for each instance

        addMapButton();

        initContentFeed();
    }

    private void initContentFeed() {
        if (currentLocation != null) {
            showContentFeed();
            startAPIClient(currentLocation);
        } else {
            showLoadingSpinner();
        }
    }

    private void showLoadingSpinner() {
        progressBar.setVisibility(View.VISIBLE);
        contentFeed.setVisibility(View.INVISIBLE);
    }

    private void showContentFeed() {
        progressBar.setVisibility(View.INVISIBLE);
        contentFeed.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!PermissionUtils.isLocationPermissionGranted(this)) {
            PermissionUtils.requestPermission(this);
        } else {
            // Bind to the service
            Log.i(TAG, "Binding LocationUpdateService.");
            bindService(new Intent(this, LocationUpdateService.class), locationServiceConnection,
                    Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i(TAG, "Registering LocationUpdateReceiver.");
        LocalBroadcastManager.getInstance(this).registerReceiver(locationUpdateReceiver,
                new IntentFilter(LocationUpdateService.ACTION_BROADCAST));
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "Unregistering LocationUpdateReceiver.");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationUpdateReceiver);

        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBoundToLocationUpdates) {
            // TODO - This method was borrowed from Google... figure out what's happening here re: foreground
            // Unbind from the service. This signals to the service that this activity is no longer
            // in the foreground, and the service can respond by promoting itself to a foreground
            // service.
            Log.i(TAG, "Unbinding LocationUpdateService.");
            unbindService(locationServiceConnection);
            isBoundToLocationUpdates = false;
        }
    }

    private Geocode convertLocationToGeocode(Location location, int radius) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        return new Geocode(lat, lon, radius, Geocode.Distance.KILOMETERS);
    }

//    private Boolean getCurrentLocation() {
//        if (ActivityCompat.checkSelfPermission(this,
//                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            // Permission to access the location is missing.
//            PermissionUtils.requestPermission(this);
//        } else {
//            mFusedLocationClient.getLastLocation()
//                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
//                @Override
//                public void onSuccess(Location location) {
//                    Toast.makeText(getApplicationContext(), "Location obtained.",
//                            Toast.LENGTH_LONG).show();
//                }
//            });
//        }
//
//        return false;
//
////        try {
////            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
////            String provider = locationManager.getBestProvider(new Criteria(), false);
////            if (provider != null) {
////                Location location = locationManager.getLastKnownLocation(provider);
////                double lat = location.getLatitude();
////                double lon = location.getLongitude();
////                currentLocation = new Geocode(lat, lon, 1, Geocode.Distance.KILOMETERS);
////                return true;
////            }
////        } catch (SecurityException e) {
////            Toast.makeText(getApplicationContext(), "Location access is not enabled.", Toast.LENGTH_SHORT).show();
////        }
////
////        return false;
//    }

    private void addMapButton() {
        Button goToMap = findViewById(R.id.button_id);
        goToMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent mapIntent = new Intent(getApplicationContext(), GoogleMapsActivity.class);
                mapIntent.putExtra("tweet_map", tweetIdCoordinates);
                startActivity(mapIntent);
            }
        });

    }

    private void startAPIClient(Geocode currentLocation) {
        TwitterCore twitterCore = TwitterCore.getInstance();
        TwitterApiClient client = twitterCore.getApiClient();
        final SearchService searchService = client.getSearchService();

        Call<Search> secondCall = searchService.tweets("", FeedActivity.currentLocation, null, null, null, 10, null, lastSinceId, null, null);

        secondCall.enqueue(new Callback<Search>() {
            @Override
            public void onResponse(Call<Search> call, Response<Search> response) {
                parseSearchResponse(response);
            }

            @Override
            public void onFailure(Call<Search> call, Throwable t) {
                Toast.makeText(FeedActivity.this, "Could not find tweets near you.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void parseSearchResponse(Response<Search> response) {
        Search results = response.body();
        tweets = results.tweets;

        if (tweets.isEmpty()) {
            Toast.makeText(FeedActivity.this, "No new tweets near you!", Toast.LENGTH_SHORT).show();
        } else {
            linearLayout.removeAllViews();
            for (Tweet tweet : tweets) {
                TweetView tweetView = new TweetView(FeedActivity.this, tweet);
                linearLayout.addView(tweetView);

                if (tweet.coordinates != null || tweet.place != null) {
                    Double lat;
                    Double lon;
                    if (tweet.coordinates != null) {
                        lat = tweet.coordinates.getLatitude();
                        lon = tweet.coordinates.getLongitude();
                    } else {

                        lon = tweet.place.boundingBox.coordinates.get(0).get(0).get(0);
                        lat = tweet.place.boundingBox.coordinates.get(0).get(0).get(1);
                    }

                    LatLng coords = new LatLng(lat, lon);

                    tweetIdCoordinates.put(tweet.id, coords);
                }

            }
            showContentFeed();
        }

    }

    /**
     * Receiver for broadcasts sent by LocationUpdateService.
     */
    private class LocationUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            loc = intent.getParcelableExtra(LocationUpdateService.UPDATED_LOCATION);
            currentLocation = convertLocationToGeocode(loc, 1);
            Toast.makeText(FeedActivity.this, "Received new location update.", Toast.LENGTH_SHORT).show();
            if (firstRun) { // TODO - This is for debugging...remove eventually
                startAPIClient(currentLocation);
                firstRun = false;
            } else {
                if (significantDistanceMoved()) {
                    startAPIClient(currentLocation);
                } else {
                    Toast.makeText(FeedActivity.this, "Not updating content feed.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private boolean significantDistanceMoved() {
        return false;
    }
}
