package intl.who.covid19;

import androidx.annotation.NonNull;

public class Location {

    // Do not rename these fields! They are named to match API
    private double latitude;
    private double longitude;
    private Integer accuracy;
    private long timestamp;

    public Location() { }
    public Location(@NonNull android.location.Location src) {
        latitude = src.getLatitude();
        longitude = src.getLongitude();
        accuracy = src.hasAccuracy() ? (int)src.getAccuracy() : null;
        timestamp = src.getTime() / 1000;
    }
}
