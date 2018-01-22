package com.nwhacksjss.android.nwhacks.utils;

import android.location.Location;

import com.twitter.sdk.android.core.services.params.Geocode;

public abstract class LocationUtils {
    public static Geocode convertLocationToGeocode(Location location, int radius) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        return new Geocode(lat, lon, radius, Geocode.Distance.KILOMETERS);
    }

    public static boolean significantLocationChange(Location location, Location newLocation) {
        return true;
    }
}
