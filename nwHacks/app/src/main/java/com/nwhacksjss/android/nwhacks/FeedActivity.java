package com.nwhacksjss.android.nwhacks;

import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
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
    private HashMap<Long, LatLng> tweetIdCoordinates= new HashMap<>();
    private List<Tweet> tweets;
    private ArrayList<LatLng> tweetCoords;
    private LinearLayout linearLayout;
    private Long lastSinceId;
    private static Geocode currentLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed);

        tweetCoords = new ArrayList<>();
        tweets = new ArrayList<>();
        lastSinceId = 951701941301624832l; // TODO: generate new sinceId for each instance
        linearLayout = findViewById(R.id.feed_layout);

        addMapButton();

        if (getCurrentLocation()) {
            startAPIClient(currentLocation);
        } else Toast.makeText(getApplicationContext(), "Cannot find current location.", Toast.LENGTH_SHORT).show();
    }

    private Boolean getCurrentLocation() {
        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            String provider = locationManager.getBestProvider(new Criteria(), false);
            if (provider != null) {
                Location location = locationManager.getLastKnownLocation(provider);
                double lat = location.getLatitude();
                double lon = location.getLongitude();
                currentLocation = new Geocode(lat, lon, 1, Geocode.Distance.KILOMETERS);
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
}
