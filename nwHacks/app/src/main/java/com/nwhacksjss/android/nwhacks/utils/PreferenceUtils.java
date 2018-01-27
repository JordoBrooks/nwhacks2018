package com.nwhacksjss.android.nwhacks.utils;

import android.content.Context;
import android.preference.PreferenceManager;

public class PreferenceUtils {
    private static final String KEY_TRACK_ME_MODE = "track_me_mode";

    public static void setPrefTrackMeMode(Context context, boolean isActive) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_TRACK_ME_MODE, isActive)
                .apply();
    }

    public static boolean getTrackMeModeStatus(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_TRACK_ME_MODE, false);
    }
}
