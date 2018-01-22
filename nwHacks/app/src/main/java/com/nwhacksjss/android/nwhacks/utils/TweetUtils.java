package com.nwhacksjss.android.nwhacks.utils;

import com.twitter.sdk.android.core.models.Tweet;

import java.util.List;

public abstract class TweetUtils {
    public static boolean tweetSetDiffers(List<Tweet> tweets, List<Tweet> newTweets) {
        return true;
    }

    public static int findNumNewTweets(List<Tweet> tweets, List<Tweet> newTweets) {
        return 7;
    }
}
