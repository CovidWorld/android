package intl.who.covid19.ui;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import intl.who.covid19.App;
import intl.who.covid19.R;

public class StatusActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);
        ((TextView) findViewById(R.id.textView_bluetooth)).setText(Html.fromHtml(getString(R.string.status_bluetooth)));
        ((TextView) findViewById(R.id.textView_location)).setText(Html.fromHtml(getString(R.string.status_location)));
        ((TextView) findViewById(R.id.textView_internet)).setText(Html.fromHtml(getString(R.string.status_internet)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        updateUi();
    }

    private void updateUi() {
        int dp4 = (int) (4 * getResources().getDisplayMetrics().density);
        // Check BLE support / BT enabled
        final boolean bleSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
        final boolean btEnabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        Button buttonBluetooth = findViewById(R.id.button_bluetooth);
        buttonBluetooth.setBackgroundResource(bleSupported && btEnabled ? R.drawable.bg_btn_green : R.drawable.bg_btn_red);
        buttonBluetooth.setPadding(dp4 * 2, dp4, dp4 * 2, dp4);
        buttonBluetooth.setText(!bleSupported ? R.string.status_unsupported : !btEnabled ? R.string.status_enable : R.string.status_enabled);
        buttonBluetooth.setOnClickListener(v -> {
            if (bleSupported && !btEnabled) {
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            }
        });
        // Check GPS support / enabled
        final boolean gpsSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        final boolean gpsEnabled = locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // Check permissions
        boolean granted = true;
        for (String permission : App.PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }
        boolean permissionsGranted = granted;
        Button buttonLocation = findViewById(R.id.button_location);
        buttonLocation.setBackgroundResource(gpsSupported && gpsEnabled && permissionsGranted ? R.drawable.bg_btn_green : R.drawable.bg_btn_red);
        buttonLocation.setPadding(dp4 * 2, dp4, dp4 * 2, dp4);
        buttonLocation.setText(!gpsSupported ? R.string.status_unsupported : !gpsEnabled || !permissionsGranted ? R.string.status_enable : R.string.status_enabled);
        buttonLocation.setOnClickListener(v -> {
            if (gpsSupported && !gpsEnabled) {
                LocationRequest locationRequest = LocationRequest.create();
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                LocationSettingsRequest req = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build();
                LocationServices.getSettingsClient(this).checkLocationSettings(req).addOnCompleteListener(this, task -> {
                    try {
                        task.getResult(ApiException.class);
                    } catch (ApiException e) {
                        switch (e.getStatusCode()) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                try {
                                    ((ResolvableApiException) e).startResolutionForResult(this, 0);
                                } catch (IntentSender.SendIntentException ex) {
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                updateUi();
                                break;
                        }
                    }
                });
            } else if (gpsEnabled && !permissionsGranted) {
                requestPermissions(App.PERMISSIONS, 0);
            }
        });
        // Internet connection check
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
        boolean networkConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected();
        Button buttonInternet = findViewById(R.id.button_internet);
        buttonInternet.setBackgroundResource(networkConnected ? R.drawable.bg_btn_green : R.drawable.bg_btn_red);
        buttonInternet.setPadding(dp4 * 2, dp4, dp4 * 2, dp4);
        buttonInternet.setText(!networkConnected ? R.string.status_activate : R.string.status_active);
        buttonInternet.setOnClickListener(v -> {
            if (!networkConnected) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
    }
}
