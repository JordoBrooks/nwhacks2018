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
        if (location == null) {
            return true;
        }

        double latitude = location.getLatitude();
        double newLatitude = newLocation.getLatitude();

        double longitude = location.getLongitude();
        double newLongitude = newLocation.getLongitude();

        return (Math.abs(latitude - newLatitude) >= 0.001 || Math.abs(longitude - newLongitude) >= 0.001);
    }
}
