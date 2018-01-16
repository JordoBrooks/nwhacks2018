package com.nwhacksjss.android.nwhacks;

import android.app.Application;

import com.twitter.sdk.android.core.Twitter;

public class TweetsRoundTheCorner extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Twitter.initialize(this);
    }
}
