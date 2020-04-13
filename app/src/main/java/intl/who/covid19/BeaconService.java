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

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.LongSparseArray;

import androidx.annotation.Nullable;
import androidx.core.app.AlarmManagerCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageFilter;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.MessagesClient;
import com.google.android.gms.nearby.messages.MessagesOptions;
import com.google.android.gms.nearby.messages.NearbyPermissions;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.nearby.messages.SubscribeOptions;
import com.uriio.beacons.Beacons;
import com.uriio.beacons.model.Beacon;
import com.uriio.beacons.model.iBeacon;

import java.nio.ByteBuffer;

import intl.who.covid19.ui.WelcomeActivity;

public class BeaconService extends Service {

    public static final long LOCATION_UPDATE_INTERVAL_MILLIS = 3_600_000; // 1 hour
    public static final float LOCATION_UPDATE_DISTANCE_METERS = 1000; // 1 km

    private static final String ACTION_RESTART_LOCATION = "intl.who.covid19.ACTION_RESTART_LOCATION";

    @SuppressWarnings({"WeakerAccess", "unused"})
    public class Binder extends android.os.Binder {
        public boolean isStarted() {
            return messagesClient != null;
        }
        public int getBeaconsNearby() {
            return liveEncounters.size();
        }
        public void cutLiveEncounters() {
            BeaconService.this.cutLiveEncounters();
        }
    }

    private final Binder binder = new Binder();
    private NotificationCompat.Builder notificationBuilder;
    private MessagesClient messagesClient;
    private MessageListener messageListener;
    private Beacon beacon;
    private final LongSparseArray<Encounter> liveEncounters = new LongSparseArray<>();
    private final BtStateReceiver btStateReceiver = new BtStateReceiver();
    private final GpsStateReceiver gpsStateReceiver = new GpsStateReceiver();
    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            // Restart location updates with appropriate configuration
            // (inQuarantine flag has changed since last location update request)
            boolean inQuarantine = App.get(BeaconService.this).isInQuarantine();
            if (locationHighPowerStarted != null && locationHighPowerStarted != inQuarantine) {
                App.log("Restarting location update service due to the changed quarantine value");
                startService(getLocationIntent());
                return;
            }

            // Ignore location updates when not in quarantine
            // In this case we use only last known location when needed
            if (!inQuarantine) {
                return;
            }

            // Pick the most precise location from all locations
            Location best = locationResult.getLastLocation();
            if (best.hasAccuracy()) {
                for (Location l : locationResult.getLocations()) {
                    if (l.hasAccuracy() && l.getAccuracy() < best.getAccuracy()) {
                        best = l;
                    }
                }
            }
            onQuarantineLocation(best);

            if (best.hasAccuracy() && best.getAccuracy() <= 20) {
                // No need to get more locations, this is accurate enough
                // This is battery optimization
                App.get(BeaconService.this).getFusedLocationClient().removeLocationUpdates(this);
            }
        }
    };
    // To track which location update is started
    private Boolean locationHighPowerStarted = null;
    private boolean locatedAtHome = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Beacons.initialize(this);
        notificationBuilder = new NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_PERSISTENT)
                .setOngoing(true)
                .setColor(ResourcesCompat.getColor(getResources(), R.color.colorPrimaryDark, null))
                .setSmallIcon(R.drawable.ic_notification_scan)
                .setContentTitle(getText(R.string.notification_scan_title))
                .setContentIntent(PendingIntent.getActivity(this, 1, new Intent(this, WelcomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT));
        if (beacon == null) {
            ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
            bb.putLong(BuildConfig.BEACON_UUID.getMostSignificantBits());
            bb.putLong(BuildConfig.BEACON_UUID.getLeastSignificantBits());
            long deviceId = App.get(this).prefs().getLong(Prefs.DEVICE_ID, 0L);
            beacon = new iBeacon(bb.array(), (int) ((deviceId >> 16) & 0xffff), (int) (deviceId & 0xffff), "COVID-19");
        }
        locationHighPowerStarted = null;
        locatedAtHome = App.get(this).isInQuarantine();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final boolean termsAgreed = App.get(this).prefs().getBoolean(Prefs.TERMS, false);
        // Check BLE support / BT enabled
        final boolean bleSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
        final boolean btEnabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        // Check GPS support / enabled
        final boolean inQuarantine = App.get(BeaconService.this).isInQuarantine();
        final boolean gpsSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        final boolean gpsEnabled = locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // In case that GPS is not supported or not in quarantine, don't consider it an error
        final boolean gpsOk = !inQuarantine || !gpsSupported || gpsEnabled;
        // Check permissions
        boolean permissionsGranted = true;
        for (String permission : App.PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false;
                break;
            }
        }
        // In case that BLE is not supported, don't consider it an error
        final boolean showOk = termsAgreed && permissionsGranted && (!bleSupported || btEnabled) && gpsOk;
        updateNotification(showOk);

        boolean justRestartLocation = intent != null && ACTION_RESTART_LOCATION.equals(intent.getAction());
        if (!justRestartLocation && termsAgreed && bleSupported && btEnabled && permissionsGranted) {
            startBtlUpdates();
        }
        if (isLocationRequested() && termsAgreed && permissionsGranted) {
            startLocationUpdates();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        stopBtlUpdates();
        stopLocationUpdates();
        cutLiveEncounters();
        synchronized (liveEncounters) {
            liveEncounters.clear();
        }
        super.onDestroy();
    }

    private void updateNotification(boolean statusOk) {
        String status = getString(statusOk ? R.string.notification_scan_text : R.string.notification_scan_problem);
        startForeground(App.NOTIFICATION_ID_PERSISTENT, notificationBuilder
                .setContentText(status)
                .setColor(ContextCompat.getColor(this, statusOk ? R.color.colorAccent : R.color.red))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(status))
                .build());
    }

    private void updateNotificationSubtext() {
        startForeground(App.NOTIFICATION_ID_PERSISTENT, notificationBuilder
                .setSubText(String.valueOf(liveEncounters.size()))
                .build());
    }

    private void startBtlUpdates() {
        btStateReceiver.register();
        if (messagesClient == null) {
            messagesClient = Nearby.getMessagesClient(this, new MessagesOptions.Builder().setPermissions(NearbyPermissions.BLE).build());
            messageListener = new MessageListener() {
                @Override
                public void onFound(Message message) {
                    long deviceId = getDeviceId(message);
                    boolean inQuarantine = App.get(BeaconService.this).isInQuarantine();
                    App.log("onFound: " + deviceId);
                    synchronized (liveEncounters) {
                        if (liveEncounters.get(deviceId) == null) {
                            Encounter enc = new Encounter(deviceId);
                            if (isLocationRequested()) {
                                enc.setLocation(App.get(BeaconService.this).getLastLocation(), inQuarantine ? -1 : (int) App.get(BeaconService.this).getRemoteConfig().getLong(App.RC_IBEACON_LOCATION_ACCURACY));
                            }
                            liveEncounters.put(deviceId, enc);
                            updateNotificationSubtext();
                        }
                    }
                }
                @Override
                public void onLost(Message message) {
                    long deviceId = getDeviceId(message);
                    App.log("onLost: " + deviceId);
                    synchronized (liveEncounters) {
                        Encounter enc = liveEncounters.get(deviceId);
                        if (enc != null) {
                            liveEncounters.remove(deviceId);
                            updateNotificationSubtext();
                            long duration = System.currentTimeMillis() / 1000 - enc.getTimestamp();
                            if (duration >= App.get(BeaconService.this).getRemoteConfig().getLong(App.RC_MIN_ENCOUNTER_DURATION)) {
                                enc.setDuration(duration);
                                App.get(BeaconService.this).getEncounterQueue().add(enc);
                            }
                        }
                    }
                }
            };
            messagesClient.subscribe(messageListener, new SubscribeOptions.Builder()
                    .setStrategy(Strategy.BLE_ONLY)
                    .setFilter(new MessageFilter.Builder().includeIBeaconIds(BuildConfig.BEACON_UUID, null, null).build())
                    .build());
            App.log("Beacon listener started");
        }
        if (beacon.start()) {
            App.log("Beacon started");
        }
    }

    private void stopBtlUpdates() {
        btStateReceiver.unregister();
        if (messagesClient != null) {
            messagesClient.unsubscribe(messageListener);
            messagesClient = null;
            messageListener = null;
            App.log("Beacon listener stopped");
        }
        beacon.stop();
        App.log("Beacon stopped");
    }

    private long getDeviceId(Message msg) {
        byte[] content = msg.getContent();
        return content.length >= 20 ? content[16] << 24 | content[17] << 16 | content[18] << 8 | content[19] : -1L;
    }

    private void cutLiveEncounters() {
        App app = App.get(this);
        Location location = app.getLastLocation();
        boolean inQuarantine = app.isInQuarantine();
        synchronized (liveEncounters) {
            long now = System.currentTimeMillis() / 1000;
            app.getEncounterQueue().setAutoSave(false);
            for (int i = 0; i < liveEncounters.size(); i++) {
                Encounter enc = liveEncounters.valueAt(i);
                long duration = now - enc.getTimestamp();
                // Only cut encounters that meet the min duration, leave the others as they are
                if (duration >= app.getRemoteConfig().getLong(App.RC_MIN_ENCOUNTER_DURATION)) {
                    enc.setDuration(duration);
                    app.getEncounterQueue().add(enc);
                    // continue to track the encounter
                    Encounter newEnc = new Encounter(enc.getSeenProfileId());
                    if (isLocationRequested()) {
                        newEnc.setLocation(location, inQuarantine ? -1 : (int) App.get(this).getRemoteConfig().getLong(App.RC_IBEACON_LOCATION_ACCURACY));
                    }
                    liveEncounters.setValueAt(i, newEnc);
                }
            }
            app.getEncounterQueue().save();
        }
    }

    private void startLocationUpdates() {
        App app = App.get(this);
        LocationRequest locationRequest = LocationRequest.create();
        if (app.isInQuarantine()) {
            gpsStateReceiver.register();
            long waitMillis = app.getRemoteConfig().getLong(App.RC_QUARANTINE_LOCATION_WAIT_DURATION) * 1000;
            long periodMillis = app.getRemoteConfig().getLong(App.RC_QUARANTINE_LOCATION_PERIOD) * 60_000;
            locationHighPowerStarted = true;
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    // 10 consecutive updates should be enough to get precise enough location
                    // This is battery optimization when larger wait duration is used
                    .setNumUpdates(10)
                    // Supply small number (a few seconds) to make location request accurate
                    .setInterval(1000).setFastestInterval(1000)
                    // Use wait time to deliver acquired locations together (and be able to pick the best one to use)
                    .setMaxWaitTime(waitMillis / 4)
                    .setExpirationDuration(waitMillis);
            // This location request is "one shot", must schedule new request after defined period
            scheduleNextLocationUpdate(waitMillis + periodMillis);
        } else {
            gpsStateReceiver.unregister();
            locationHighPowerStarted = false;
            locationRequest.setPriority(LocationRequest.PRIORITY_LOW_POWER)
                    .setInterval(LOCATION_UPDATE_INTERVAL_MILLIS)
                    .setSmallestDisplacement(LOCATION_UPDATE_DISTANCE_METERS);
            // This location request is continuous, must cancel any pending location updates
            cancelNextLocationUpdate();
            // Also cancel notification when no longer in quarantine
            cancelQuarantineLeftNotification();
        }
        app.getFusedLocationClient().requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        App.log("Location updates started (" + (locationHighPowerStarted ? "high" : "low") + ")");
    }

    private void stopLocationUpdates() {
        gpsStateReceiver.unregister();
        cancelNextLocationUpdate();
        App.get(this).getFusedLocationClient().removeLocationUpdates(locationCallback);
        App.log("Location updates stopped");
        locationHighPowerStarted = null;
    }

    @SuppressWarnings("ConstantConditions")
    private void scheduleNextLocationUpdate(long afterDurationMillis) {
        long nextLocationUpdateAt = SystemClock.elapsedRealtime() + afterDurationMillis;
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        AlarmManagerCompat.setAndAllowWhileIdle(am, AlarmManager.ELAPSED_REALTIME_WAKEUP, nextLocationUpdateAt, getLocationPendingIntent());
        App.log("Scheduled location request refresh in " + (afterDurationMillis / 60_000d) + " minutes");
    }

    @SuppressWarnings("ConstantConditions")
    private void cancelNextLocationUpdate() {
        ((AlarmManager) getSystemService(ALARM_SERVICE)).cancel(getLocationPendingIntent());
    }

    private PendingIntent getLocationPendingIntent() {
        return PendingIntent.getService(this, 1, getLocationIntent(), PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Intent getLocationIntent() {
        return new Intent(this, getClass()).setAction(ACTION_RESTART_LOCATION);
    }

    /** Called only if in quarantine */
    private void onQuarantineLocation(Location location) {
        App.log("onQuarantineLocation: " + location);
        App.get(this).getLocationQueue().add(new intl.who.covid19.Location(location));

        // Calculate distance to home address
        Location homeLocation = new Location("");
        homeLocation.setLatitude(App.get(this).prefs().getFloat(Prefs.HOME_LAT, 0f));
        homeLocation.setLongitude(App.get(this).prefs().getFloat(Prefs.HOME_LNG, 0f));
        float distance = location.distanceTo(homeLocation);
        double radius = App.get(this).getRemoteConfig().getDouble(App.RC_QUARANTINE_RADIUS);
        boolean atHome = distance - location.getAccuracy() <= radius;
        if (locatedAtHome && !atHome) {
            // Left home
            showQuarantineLeftNotification();
            new Api(this).quarantineLeft(new Api.QuarantineLeftRequest(
                    App.get(this).prefs().getString(Prefs.DEVICE_UID, null),
                    App.get(this).prefs().getLong(Prefs.DEVICE_ID, 0), location), (status, response) -> {
                // Nothing much to do here
            });
        } else if (!locatedAtHome && atHome) {
            // Entered home
           cancelQuarantineLeftNotification();
        }
        locatedAtHome = atHome;
    }

    private void showQuarantineLeftNotification() {
        String message = App.get(this).getRemoteConfig().getString(App.RC_QUARANTINE_LEFT_MESSAGE);
        Notification notification = new NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ALARM)
                .setOngoing(true)
                .setColor(ResourcesCompat.getColor(getResources(), R.color.red, null))
                .setSmallIcon(R.drawable.ic_notification_warning)
                .setContentTitle(getText(R.string.notification_quarantineLeft_title))
                .setContentText(message)
                .setContentIntent(PendingIntent.getActivity(this, 1, new Intent(this, WelcomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .build();
        NotificationManagerCompat.from(this).notify(App.NOTIFICATION_ID_QUARANTINE_LEFT, notification);
    }

    private void cancelQuarantineLeftNotification() {
        NotificationManagerCompat.from(this).cancel(App.NOTIFICATION_ID_QUARANTINE_LEFT);
    }

    private boolean isLocationRequested() {
        App app = App.get(this);
        return app.getRemoteConfig().getLong(App.RC_IBEACON_LOCATION_ACCURACY) >= 0 || app.isInQuarantine();
    }

    private class BtStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())
                    && intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                App.log("Bluetooth is turned off");
                updateNotification(false);
            }
        }
        void register() {
            registerReceiver(this, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        }
        void unregister() {
            try {
                unregisterReceiver(this);
            } catch (RuntimeException e) {
            }
        }
    }

    private class GpsStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (LocationManager.PROVIDERS_CHANGED_ACTION.equals(intent.getAction())
                    && locationHighPowerStarted != null && locationHighPowerStarted) {
                LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    App.log("GPS is turned off");
                    updateNotification(false);
                }
            }
        }
        void register() {
            registerReceiver(this, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
        }
        void unregister() {
            try {
                unregisterReceiver(this);
            } catch (RuntimeException e) {
            }
        }
    }

}
