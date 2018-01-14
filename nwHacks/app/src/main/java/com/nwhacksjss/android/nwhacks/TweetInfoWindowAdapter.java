package com.nwhacksjss.android.nwhacks;

import android.app.Activity;
import android.view.View;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterApiClient;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.models.Tweet;
import com.twitter.sdk.android.core.services.StatusesService;
import com.twitter.sdk.android.tweetui.TweetUtils;
import com.twitter.sdk.android.tweetui.TweetView;

import retrofit2.Call;

import static java.security.AccessController.getContext;

/**
 * Created by jordan on 14/01/18.
 */

public class TweetInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

    View view;
    Tweet tweet;
    private Activity context;

    public TweetInfoWindowAdapter(Activity context){
        this.context = context;
    }

    @Override
    public View getInfoWindow(Marker marker) {
        long id = Long.parseLong(marker.getTitle());

        TwitterApiClient twitterApiClient = TwitterCore.getInstance().getApiClient();
        StatusesService statusesService = twitterApiClient.getStatusesService();
        Call<Tweet> call = statusesService.show(id, null, null, null);
        call.enqueue(new Callback<Tweet>() {
            @Override
            public void success(Result<Tweet> result) {
                tweet = result.data;
            }

            public void failure(TwitterException exception) {
                tweet = null;
            }
        });

        if (tweet != null) {
            // ideally would create View of tweet here
            view = new TweetView(context, tweet);
        } else {
            view = null;
        }
        return view;
    }

    @Override
    public View getInfoContents(Marker marker) {
        return null;
    }
}
