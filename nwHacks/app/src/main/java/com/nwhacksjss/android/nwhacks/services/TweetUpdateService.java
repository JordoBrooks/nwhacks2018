package com.nwhacksjss.android.nwhacks.services;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;
import com.nwhacksjss.android.nwhacks.FeedActivity;
import com.nwhacksjss.android.nwhacks.R;
import com.nwhacksjss.android.nwhacks.utils.LocationUtils;
import com.nwhacksjss.android.nwhacks.utils.PreferenceUtils;
import com.nwhacksjss.android.nwhacks.utils.TweetUtils;
import com.twitter.sdk.android.core.TwitterApiClient;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.models.Search;
import com.twitter.sdk.android.core.models.Tweet;
import com.twitter.sdk.android.core.services.SearchService;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TweetUpdateService extends Service {

    private static final String TAG = TweetUpdateService.class.getSimpleName();

    public static final String ACTION_BROADCAST =
            TweetUpdateService.class.getCanonicalName() + ".broadcast";

    public static final String UPDATED_TWEETS =
            TweetUpdateService.class.getCanonicalName() + ".location";

    /**
     * The name of the channel for notifications.
     */
    private static final String CHANNEL_ID = "channel_01";

    private static final String STARTED_FROM_NOTIFICATION =
            TweetUpdateService.class.getCanonicalName() + ".started_from_notification";

    /**
     * The identifier for the notification displayed for the foreground service.
     */
    private static final int NOTIFICATION_ID = 12345678;

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private boolean changingConfiguration = false;

    private NotificationManager notificationManager;

    private final IBinder binder = new LocalBinder();

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 20000;

    /**
     * The fastest rate for active location updates. Updates will never be more frequent
     * than this value.
     */
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 10000;

    /**
     * Contains parameters used by Fused Location Provider API.
     */
    private LocationRequest locationRequest;

    /**
     * Provides access to the Fused Location Provider API.
     */
    private FusedLocationProviderClient fusedLocationClient;

    /**
     * Callback for changes in location.
     */
    private LocationCallback locationCallback;

    private Handler serviceHandler;

    private static boolean trackMeMode;

    private Long lastSinceId;

    /**
     * The current location.
     */
    private Location location;

    private List<Tweet> tweets;

    private Context context;

    public TweetUpdateService() {
    }

    @Override
    public void onCreate() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        context = this;

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                Location newLocation = locationResult.getLastLocation();

                handleUpdatedLocation(newLocation);
            }
        };

        lastSinceId = 951701941301624832l; // TODO: generate new sinceId for each instance

        createLocationRequest();

        // Create HandlerThread for getting location updates
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        serviceHandler = new Handler(handlerThread.getLooper());
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            // Create the channel for the notification
            NotificationChannel mChannel =
                    new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);

            // Set the Notification Channel for the Notification Manager.
            notificationManager.createNotificationChannel(mChannel);
        }
    }

    private void handleUpdatedLocation(Location newLocation) {
        if (LocationUtils.significantLocationChange(location, newLocation)) {
            location = newLocation;
            Toast.makeText(this, "New location - updating tweets.", Toast.LENGTH_SHORT).show();
            updateTweetsFromTwitterApi();
        } else {
            Toast.makeText(this, "New location but non-significant change.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateTweetsFromTwitterApi() {
        TwitterCore twitterCore = TwitterCore.getInstance();
        TwitterApiClient client = twitterCore.getApiClient();
        final SearchService searchService = client.getSearchService();

        Call<Search> secondCall = searchService.tweets("", LocationUtils.convertLocationToGeocode(location, 1), null, null, null, 10, null, lastSinceId, null, null);

        secondCall.enqueue(new Callback<Search>() {
            @Override
            public void onResponse(Call<Search> call, Response<Search> response) {
                Search results = response.body();
                List<Tweet> newTweets = results.tweets;

                if (TweetUtils.tweetSetDiffers(tweets, newTweets)) {
                    Toast.makeText(getApplicationContext(), "Tweet set differed!", Toast.LENGTH_SHORT).show();
                    tweets = newTweets;

                    // Notify anyone listening for broadcasts about the new location.
                    Intent intent = new Intent(ACTION_BROADCAST);
                    Gson gson = new Gson();
                    ArrayList<String> tweetsAsStrings = new ArrayList<>();
                    for (Tweet tweet : tweets) {
                        tweetsAsStrings.add(gson.toJson(tweet));
                    }
                    intent.putStringArrayListExtra(UPDATED_TWEETS, tweetsAsStrings);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

                    // Update notification content if running as a foreground service.
                    if (serviceIsRunningInForeground(context)) {
                        int numNew = TweetUtils.findNumNewTweets(tweets, newTweets);
                        Notification n = getNotification(numNew);
                        notificationManager.notify(NOTIFICATION_ID, n);
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Same tweets.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Search> call, Throwable t) {
                Toast.makeText(getApplicationContext(), "Could not find tweets near you.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started");
        boolean startedFromNotification = intent.getBooleanExtra(STARTED_FROM_NOTIFICATION,
                false);

        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification) {
            PreferenceUtils.setPrefTrackMeMode(context, false);
            removeTweetUpdates();
            stopSelf();
        }
        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        changingConfiguration = true;
    }

    private void removeTweetUpdates() {
        Log.i(TAG, "Removing location updates");
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            stopSelf();
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission. Could not remove updates. " + unlikely);
        }
    }

    @Override
    public void onDestroy() {
        serviceHandler.removeCallbacksAndMessages(null);
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        Log.i(TAG, "in onBind()");
        stopForeground(true);
        changingConfiguration = false;
        return binder;
    }

    @Override
    public void onRebind(Intent intent) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.i(TAG, "in onRebind()");
        stopForeground(true);
        changingConfiguration = false;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Last client unbound from service");

        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!changingConfiguration && trackMeMode) {
            Log.i(TAG, "Starting foreground service");
            startForeground(NOTIFICATION_ID, getNotification());
        } else {
            removeTweetUpdates();
            stopSelf();
        }
        return true; // Ensures onRebind() is called when a client re-binds.
    }

    public void requestTweetUpdates() {
        Log.i(TAG, "Requesting location updates");
        startService(new Intent(getApplicationContext(), TweetUpdateService.class));
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest,
                    locationCallback, Looper.myLooper());
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission. Could not request updates. " + unlikely);
        }
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public TweetUpdateService getService() {
            return TweetUpdateService.this;
        }
    }

    /**
     * Returns the first notification to be used as part of the foreground service.
     */
    private Notification getNotification() {
        Intent intent = new Intent(this, TweetUpdateService.class);

        CharSequence text = "Looking for tweets...";

        // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
        intent.putExtra(STARTED_FROM_NOTIFICATION, true);

        // The PendingIntent that leads to a call to onStartCommand() in this service.
        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // The PendingIntent to launch activity.
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, FeedActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .addAction(R.drawable.launcher_icon, "Back to app",
                        activityPendingIntent)
                .addAction(R.drawable.launcher_icon, "Stop tracking me",
                        servicePendingIntent)
                .setContentText(text)
                .setContentTitle("Tweet Tracker")
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.launcher_icon)
                .setTicker(text)
                .setWhen(System.currentTimeMillis());

        return builder.build();
    }

    /**
     * Returns a notification to be used as part of the foreground service.
     * @param numNew
     */
    private Notification getNotification(int numNew) {
        Intent intent = new Intent(this, TweetUpdateService.class);

        CharSequence text = "There " + (numNew == 1 ? "is " : "are ") +  numNew + " new "
                + (numNew == 1 ? "tweet " : "tweets ") + "near you!";

        // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
        intent.putExtra(STARTED_FROM_NOTIFICATION, true);

        // The PendingIntent that leads to a call to onStartCommand() in this service.
        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // The PendingIntent to launch activity.
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, FeedActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .addAction(R.drawable.launcher_icon, "Back to app",
                        activityPendingIntent)
                .addAction(R.drawable.launcher_icon, "Stop tracking me",
                        servicePendingIntent)
                .setContentText(text)
                .setContentTitle("Tweet Tracker")
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.launcher_icon)
                .setTicker(text)
                .setWhen(System.currentTimeMillis());

        return builder.build();
    }

    /**
     * Returns true if this is a foreground service.
     *
     * @param context The {@link Context}.
     */
    public boolean serviceIsRunningInForeground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (getClass().getName().equals(service.service.getClassName())) {
                if (service.foreground) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void setTrackMeMode(boolean trackMeMode) {
        TweetUpdateService.trackMeMode = trackMeMode;
    }
}
