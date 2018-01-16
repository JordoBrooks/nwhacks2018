package com.nwhacksjss.android.nwhacks;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterLoginButton;

/**
 * A login screen that offers login via Twitter.
 */
public class LoginActivity extends AppCompatActivity {

    // UI references.
    private TwitterLoginButton mLoginButton;
    private TwitterSession session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.setTitle(getResources().getString(R.string.title_activity_login));
        System.out.println("onCreate started");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mLoginButton = (TwitterLoginButton) findViewById(R.id.login_button);

        mLoginButton.setCallback(new Callback<TwitterSession>() {
            @Override
            public void success(Result<TwitterSession> result) {
                Toast.makeText(LoginActivity.this, "IT WORKS!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(getApplicationContext(), FeedActivity.class);
                startActivity(intent);
            }

            @Override
            public void failure(TwitterException exception) {
                Toast.makeText(LoginActivity.this, "IT FAILED!", Toast.LENGTH_SHORT).show();
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Pass the activity result to the login button.
        mLoginButton.onActivityResult(requestCode, resultCode, data);
    }
}

