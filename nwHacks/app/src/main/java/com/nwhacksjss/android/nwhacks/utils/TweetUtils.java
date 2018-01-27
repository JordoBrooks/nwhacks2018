package com.nwhacksjss.android.nwhacks.utils;

import com.twitter.sdk.android.core.models.Tweet;

import java.util.List;

public abstract class TweetUtils {

    public static int findNumNewTweets(List<Tweet> tweets, List<Tweet> newTweets) {
        int numNew = 0;

        if (tweets == null) {
            numNew = newTweets.size();
        } else {
            for (Tweet newTweet : newTweets) {
                if (!tweets.contains(newTweet)) {
                    numNew++;
                }
            }
        }

        return numNew;
    }
}
