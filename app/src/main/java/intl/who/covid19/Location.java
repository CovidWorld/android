package intl.who.covid19;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public class Location {

    // Do not rename these fields! They are named to match API
    private double latitude;
    private double longitude;
    private Integer accuracy;
    @SerializedName(value = "recordTimestamp", alternate = { "timestamp" }) // For back-compatibility
    private long recordTimestamp;

    public Location() { }
    public Location(@NonNull android.location.Location src) {
        latitude = src.getLatitude();
        longitude = src.getLongitude();
        accuracy = src.hasAccuracy() ? (int)src.getAccuracy() : null;
        recordTimestamp = src.getTime() / 1000;
    }
}
