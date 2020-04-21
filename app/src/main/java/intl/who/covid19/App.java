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

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.libraries.places.api.Places;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.internal.DefaultsXmlParser;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class App extends Application {
    public interface Callback<T> {
        void onCallback(T param);
    }
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
                ContextCompat.startForegroundService(context, new Intent(context, BeaconService.class));
            }
        }
    }

    public static final String NOTIFICATION_CHANNEL_PERSISTENT = "persistent";
    public static final String NOTIFICATION_CHANNEL_ALARM = "alarm";
    public static final int NOTIFICATION_ID_PERSISTENT = 1;
    public static final int NOTIFICATION_ID_QUARANTINE_LEFT = 2;
    public static final int NOTIFICATION_ID_QUARANTINE_INFO = 3;
    /** Remote config field for api URL */
    private static final String RC_API_URL = "apiUrl";
    /** Remote config field for min. encounter duration (in seconds) */
    public static final String RC_MIN_ENCOUNTER_DURATION = "minConnectionDuration";
    /** Remote config field for encounter batch sending frequency (in minutes) */
    public static final String RC_BATCH_SEDNING_FREQUENCY = "batchSendingFrequency";
    /** Remote config field for local hotlines (JSON object of "iso2-country-code": "phone number") */
    public static final String RC_HOTLINES = "hotlines";
    /** Remote config field for quarantine location update period (in minutes) */
    public static final String RC_QUARANTINE_LOCATION_PERIOD = "quarantineLocationPeriodMinutes";
    /** Remote config field for quarantine location GPS lock wait duration (in seconds) */
    public static final String RC_QUARANTINE_LOCATION_WAIT_DURATION = "quarantineLocationWaitDurationSeconds";
    /** Remote config field for quarantine duration (in days) */
    public static final String RC_QUARANTINE_DURATION = "quarantineDuration";
    /** Remote config field for quarantine radius (in meters) */
    public static final String RC_QUARANTINE_RADIUS = "desiredPositionAccuracy";
    /** Remote config field for notification message when user leaves quarantine */
    public static final String RC_QUARANTINE_LEFT_MESSAGE = "quarantineLeftMessage";
    /** Remote config field for location accuracy in iBeacon mode (number of decimal places to round the lat/lng or -1 to disable) */
    public static final String RC_IBEACON_LOCATION_ACCURACY = "ibeaconLocationAccuracy";
    /** Remote config field for face ID confidence threshold (long) */
    public static final String RC_FACEID_CONFIDENCE_THRESHOLD = "faceIDConfidenceThreshold";
    /** Remote config field for face ID match threshold (long) */
    public static final String RC_FACEID_MATCH_THRESHOLD = "faceIDMatchThreshold";

    public static final String[] PERMISSIONS;
    static {
        List<String> perms = new ArrayList<>();
        perms.add(ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.add(ACCESS_BACKGROUND_LOCATION);
        }
        PERMISSIONS = perms.toArray(new String[0]);
    }

    public static Callback<String> logListener;
    public static void log(String msg) {
        Log.d("App.log", msg);
        Crashlytics.log(msg);
        if (logListener != null) {
            logListener.onCallback(msg);
        }
    }

    public static App get(Context context) {
        if (context instanceof Activity) {
            return (App) ((Activity) context).getApplication();
        } else {
            return (App) context.getApplicationContext();
        }
    }

    public static long parseIsoDate(String date) {
        String[] quarantineEnds = date.split("-");
        if (quarantineEnds.length < 3) {
            return 0;
        }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, Integer.parseInt(quarantineEnds[0]));
        cal.set(Calendar.MONTH, Integer.parseInt(quarantineEnds[1]) - 1);
        cal.set(Calendar.DATE, Integer.parseInt(quarantineEnds[2]));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public static long setEndOfDay(long timeInMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeInMillis);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    private SharedPreferences prefs;
    private ICountryDefaults countryDefaults;
    private EncounterQueue encounterQueue;
    private LocationQueue locationQueue;
    private FirebaseRemoteConfig remoteConfig;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        // Make sure we have a generated unique device UUID
        if (prefs.getString(Prefs.DEVICE_UID, null) == null) {
            prefs.edit().putString(Prefs.DEVICE_UID, UUID.randomUUID().toString()).apply();
        }
        // Create notification channels
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel persistent = new NotificationChannel(NOTIFICATION_CHANNEL_PERSISTENT,
                    getString(R.string.notification_channel_persistent), NotificationManager.IMPORTANCE_LOW);
            persistent.setShowBadge(false);
            NotificationChannel alarm = new NotificationChannel(NOTIFICATION_CHANNEL_ALARM,
                    getString(R.string.notification_channel_alarm), NotificationManager.IMPORTANCE_HIGH);
            alarm.setShowBadge(true);
            alarm.enableLights(true);
            alarm.setLightColor(0xffff0000);
            alarm.enableVibration(true);
            nm.createNotificationChannel(persistent);
            nm.createNotificationChannel(alarm);
        }
        // Load cached data queues
        encounterQueue = new EncounterQueue(this);
        locationQueue = new LocationQueue(this);
        // Load and configure remote config
        remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.setConfigSettingsAsync(new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build());
        Map<String, String> defaults = DefaultsXmlParser.getDefaultsFromXml(this, R.xml.remote_config_defaults);
        defaults.put(RC_API_URL, getString(R.string.defaultApiUrl));
        remoteConfig.setDefaultsAsync(new HashMap<>(defaults));
        remoteConfig.fetchAndActivate();
        // Create the fused location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        Places.initialize(this, getString(R.string.google_api_key));
        // Schedule local quarantine notification
        LocalNotificationReceiver.scheduleNotification(this);
    }

    /**
     * Use keys from {@linkplain Prefs} to manage preferences
     */
    public SharedPreferences prefs() {
        return prefs;
    }

    public ICountryDefaults getCountryDefaults() {
        if (countryDefaults == null) {
            countryDefaults = new CountryDefaults(this);
        }
        return countryDefaults;
    }

    public Uri apiUri() {
        return Uri.parse(remoteConfig.getString(RC_API_URL));
    }

    public boolean isInQuarantine() {
        return prefs.getLong(Prefs.QUARANTINE_ENDS, 0L) > System.currentTimeMillis();
    }

    public int getDaysLeftInQuarantine() {
        return (int) Math.ceil((prefs.getLong(Prefs.QUARANTINE_ENDS, 0L) - System.currentTimeMillis()) / (24.0 * 3_600_000L));
    }

    public EncounterQueue getEncounterQueue() {
        return encounterQueue;
    }

    public LocationQueue getLocationQueue() {
        return locationQueue;
    }

    public FirebaseRemoteConfig getRemoteConfig() {
        return remoteConfig;
    }

    public FusedLocationProviderClient getFusedLocationClient() {
        return fusedLocationClient;
    }

    public Location getLastLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            App.log("Missing permission to get location");
            return null;
        }
        LocationManager m = (LocationManager) getSystemService(LOCATION_SERVICE);
        return m.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
    }

    public void getLastLocation(Callback<Location> listener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            App.log("Missing permission to get location");
            listener.onCallback(null);
        }
        fusedLocationClient.getLastLocation().addOnCompleteListener(task -> listener.onCallback(task.isSuccessful() ? task.getResult() : null));
    }
}
