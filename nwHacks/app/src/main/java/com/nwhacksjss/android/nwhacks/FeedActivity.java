package com.nwhacksjss.android.nwhacks;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
import com.nwhacksjss.android.nwhacks.Utils.PermissionUtils;
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

public class FeedActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private HashMap<Long, LatLng> tweetIdCoordinates= new HashMap<>();
    private List<Tweet> tweets;
    private ArrayList<LatLng> tweetCoords;
    private LinearLayout linearLayout;
    private Long lastSinceId;
    private static Geocode currentLocation;
    private String filterBy = null;
    private int distanceRadius;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed);

        setupSharedPreferences();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(this, PermissionUtils.LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, true);
        }

        tweetCoords = new ArrayList<>();
        tweets = new ArrayList<>();
        lastSinceId = 951701941301624832l; // TODO: generate new sinceId for each instance
        linearLayout = findViewById(R.id.feed_layout);

        addMapButton();

        if (getCurrentLocation()) {
            startAPIClient(currentLocation);
        } else Toast.makeText(getApplicationContext(), "Cannot find current location.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.feed_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent openSettings = new Intent(this, SettingsActivity.class);
            startActivity(openSettings);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private Boolean getCurrentLocation() {
        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            String provider = locationManager.getBestProvider(new Criteria(), false);
            if (provider != null) {
                Location location = locationManager.getLastKnownLocation(provider);
                double lat = location.getLatitude();
                double lon = location.getLongitude();
                currentLocation = new Geocode(lat, lon, distanceRadius, Geocode.Distance.KILOMETERS);
                return true;
            }
        } catch (SecurityException e) {
            Toast.makeText(getApplicationContext(), "Location access is not enabled.", Toast.LENGTH_SHORT).show();
        }

        return false;
    }

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

        Call<Search> secondCall = searchService.tweets("", FeedActivity.currentLocation, null, null, filterBy, 10, null, lastSinceId, null, null);

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
        }
    }

    private void setupSharedPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (preferences.getBoolean("filterby_popular", false)) {
            filterBy = "popular";
        } else filterBy = null;

        distanceRadius = preferences.getInt("distance_seekbar", 1);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals("filterby_popular")) {
            if (sharedPreferences.getBoolean("filterby_popular", false)) {
                filterBy = "popular";
            } else filterBy = null;
        } else if(key.equals("distance_seekbar")) {
            distanceRadius = sharedPreferences.getInt("distance_seekbar", 1);
        }

        startAPIClient(currentLocation);
    }
}
