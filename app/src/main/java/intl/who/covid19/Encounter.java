/*-
 * Copyright (c) 2020 Sygic a.s.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package intl.who.covid19;

import android.location.Location;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Locale;

public class Encounter {

    // Do not rename these fields! They are named to match API
    private long seenProfileId;
    private long timestamp;
    private String duration;
    private Double latitude;
    private Double longitude;
    private Integer accuracy;

    public Encounter() { }
    public Encounter(long seenProfileId, boolean roundTimestampToDays) {
        this.seenProfileId = seenProfileId;
        Calendar cal = Calendar.getInstance();
        if (roundTimestampToDays) {
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 1);
            cal.set(Calendar.MILLISECOND, 0);
        }
        timestamp = cal.getTimeInMillis() / 1000;
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
    public void setLocation(@Nullable Location location, int forceAccuracy) {
        if (location == null || forceAccuracy < 0) {
            latitude = null;
            longitude = null;
            accuracy = null;
            return;
        }
        // round down to specified precision
        latitude = BigDecimal.valueOf(location.getLatitude()).setScale(forceAccuracy, RoundingMode.HALF_UP).doubleValue();
        longitude = BigDecimal.valueOf(location.getLongitude()).setScale(forceAccuracy, RoundingMode.HALF_UP).doubleValue();
        accuracy = null;
    }
}
