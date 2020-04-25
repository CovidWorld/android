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

package intl.who.covid19.ui;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.gson.Gson;

import org.hashids.Hashids;

import java.util.ArrayList;
import java.util.List;

import intl.who.covid19.Api;
import intl.who.covid19.App;
import intl.who.covid19.Prefs;
import intl.who.covid19.R;

public class WelcomeActivity extends AppCompatActivity {

	private static final int REQUEST_CODE_PERMISSIONS = 1;
	private static final int REQUEST_CODE_ENABLE_BT = 2;
	private static final int REQUEST_CODE_ENABLE_GPS = 3;
	private static final int REQUEST_CODE_PHONE_NUMBER = 4;

	private boolean skipGpsCheck;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// this must be root activity
		if (!isTaskRoot()) {
			finishAffinity();
			startActivity(new Intent(this, getClass()));
			return;
		}

		setContentView(R.layout.activity_welcome);
		((TextView) findViewById(R.id.textView_attribution)).setText(Html.fromHtml(getString(R.string.welcome_attribution)));
		if (areTermsAgreed()) {
			checkDeviceId(); // Do not check permissions here, leave it for the status view on home fragment
		}
	}

	/// Navigation

	public void onButtonAgree(View v) {
		agreeTerms();
		checkPermissionsAndContinue();
	}

	public void onPrivacy(View v) {
		startActivity(new Intent(this, PrivacyPolicyActivity.class));
	}

	private void checkPermissionsAndContinue() {
		// Check BT enabled
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter bluetoothAdapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
		if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
			startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_CODE_ENABLE_BT);
			return;
		}

		if (!skipGpsCheck && App.get(this).isInQuarantine()) {
			boolean gpsSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
			LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
			final boolean gpsEnabled = locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
			if (gpsSupported && !gpsEnabled) {
				requestGpsLocationEnabled();
				return;
			}
		}

		// Check required permissions
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			checkPermissions();
			return;
		}
		// Go to information screen
		checkDeviceId();
	}

	private void checkDeviceId() {
		SharedPreferences prefs = App.get(this).prefs();
		if (prefs.getLong(Prefs.DEVICE_ID, 0) != 0) {
			navigateNext(false);
			return;
		}
		// Register the device on server
		findViewById(R.id.button_agree).setVisibility(View.INVISIBLE);
		findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
		Api.ProfileRequest request = new Api.ProfileRequest(prefs.getString(Prefs.DEVICE_UID, null),
				prefs.getString(Prefs.FCM_TOKEN, null), null);
		new Api(this).createProfile(request, (status, response) -> {
			if (isFinishing()) {
				return;
			}
			if (status != 200) {
				findViewById(R.id.button_agree).setVisibility(View.VISIBLE);
				findViewById(R.id.progressBar).setVisibility(View.GONE);
				// Show error
				new AlertDialog.Builder(this)
						.setTitle(R.string.app_name)
						.setMessage(getString(R.string.app_apiFailed, (status + " " + response).trim()))
						.setPositiveButton(android.R.string.ok, null)
						.show();
			} else {
				Api.ProfileResponse resp = new Gson().fromJson(response, Api.ProfileResponse.class);
				App.get(this).prefs().edit()
						.putLong(Prefs.DEVICE_ID, resp.profileId)
						.putString(Prefs.COVID_ID, new Hashids("COVID-19 super-secure and unguessable hashids salt", 6, "ABCDEFGHJKLMNPQRSTUVXYZ23456789").encode(resp.profileId))
						.apply();
				verifyPhoneNumber();
			}
		});
	}

	private void verifyPhoneNumber() {
		if (App.get(this).getCountryDefaults().verifyPhoneNumberAtStart()) {
			startActivityForResult(new Intent(this, PhoneVerificationActivity.class)
					.putExtra(PhoneVerificationActivity.EXTRA_SHOW_EXPLANATION, true), REQUEST_CODE_PHONE_NUMBER);
		} else {
			navigateNext(true);
		}
	}

	private void navigateNext(boolean newProfile) {
		startActivity(new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_ASK_QUARANTINE, newProfile));
		finish();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		switch (requestCode) {
			case REQUEST_CODE_ENABLE_BT:
			case REQUEST_CODE_ENABLE_GPS:
				if (resultCode == RESULT_OK) {
					checkPermissionsAndContinue();
				}
				return;
			case REQUEST_CODE_PHONE_NUMBER:
				navigateNext(true);
				return;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private void requestGpsLocationEnabled() {
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
							((ResolvableApiException) e).startResolutionForResult(this, REQUEST_CODE_ENABLE_GPS);
						} catch (IntentSender.SendIntentException ex) {
						}
						break;
					case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
						skipGpsCheck = true;
						checkPermissionsAndContinue();
						break;
				}
			}
		});
	}

	/// Check permissions

	@RequiresApi(api = Build.VERSION_CODES.M)
	private void checkPermissions() {
		List<String> toAcquire = new ArrayList<>();
		for (String perm : App.PERMISSIONS) {
			if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
				toAcquire.add(perm);
			}
		}
		if (toAcquire.isEmpty()) {
			// all permissions granted
			checkDeviceId();
			return;
		}
		requestPermissions(toAcquire.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode == REQUEST_CODE_PERMISSIONS) {
			for (int i = 0; i < permissions.length; i++) {
				int result = grantResults[i];
				if (result != PackageManager.PERMISSION_GRANTED) {
					App.log("Permission " + permissions[i] + " not granted, result = " + result);
					return;
				}
			}
			// all permissions granted
			checkDeviceId();
			return;
		}
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	/// Preferences

	private boolean areTermsAgreed() {
		return App.get(this).prefs().getBoolean(Prefs.TERMS, false);
	}
	private void agreeTerms() {
		App.get(this).prefs().edit().putBoolean(Prefs.TERMS, true).apply();
	}
}
