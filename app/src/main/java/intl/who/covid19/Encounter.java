package intl.who.covid19;

import android.location.Location;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public class Encounter {

    private static final int MAX_ACCURACY = 1000; // 1km

    // Do not rename these fields! They are named to match API
    private long seenProfileId;
    private long timestamp;
    private String duration;
    private Double latitude;
    private Double longitude;
    private Integer accuracy;

    public Encounter() { }
    public Encounter(long seenProfileId) {
        this.seenProfileId = seenProfileId;
        timestamp = System.currentTimeMillis() / 1000;
    }

    public long getSeenProfileId() {
        return seenProfileId;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public void setDuration(long duration) {
        this.duration = String.format(Locale.ROOT, "%02d:%02d:%02d", duration / 3600, (duration % 3600) / 60, duration % 60);
    }
    public void setLocation(@Nullable Location location, boolean decreaseAccuracy) {
        if (location == null) {
            latitude = null;
            longitude = null;
            accuracy = null;
            return;
        }
        if (!decreaseAccuracy || location.hasAccuracy() && location.getAccuracy() >= MAX_ACCURACY) {
            latitude = location.getLatitude();
            longitude = location.getLongitude();
            accuracy = (int) location.getAccuracy();
        } else {
            // round down to roughly 1km precision
            latitude = BigDecimal.valueOf(location.getLatitude()).setScale(4, RoundingMode.HALF_UP).doubleValue();
            longitude = BigDecimal.valueOf(location.getLongitude()).setScale(4, RoundingMode.HALF_UP).doubleValue();
            accuracy = null;
        }
    }
}
