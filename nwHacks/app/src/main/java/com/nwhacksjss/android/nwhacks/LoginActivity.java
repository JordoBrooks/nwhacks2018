package com.nwhacksjss.android.nwhacks;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.nwhacksjss.android.nwhacks.Utils.PermissionUtils;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterLoginButton;

/**
 * A login screen that offers login via Twitter.
 */
public class LoginActivity extends AppCompatActivity {

    // UI references.
    private TwitterLoginButton mLoginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.setTitle(getResources().getString(R.string.title_activity_login));
        super.onCreate(savedInstanceState);

        requestPermission();

        setContentView(R.layout.activity_login);

        mLoginButton = findViewById(R.id.login_button);

        mLoginButton.setCallback(new Callback<TwitterSession>() {
            @Override
            // TODO - Should getApplicationContext be replaced with login activity context?
            public void success(Result<TwitterSession> result) {
                Intent intent = new Intent(getApplicationContext(), FeedActivity.class);
                startActivity(intent);
            }

            @Override
            public void failure(TwitterException exception) {
            }
        });

    }

    private void requestPermission() {
        // Check if the user has allowed the app permission to access fine location
        if (!PermissionUtils.isLocationPermissionGranted(this)) {
            // Permission to access the location is missing.
            if (PermissionUtils.shouldProvidePermissionRequestRationale(this)) {
                PermissionUtils.requestPermissionsWithRationale(this, findViewById(R.id.activity_login));
            } else {
                PermissionUtils.requestPermission(this);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Pass the activity result to the login button.
        mLoginButton.onActivityResult(requestCode, resultCode, data);
    }
}

