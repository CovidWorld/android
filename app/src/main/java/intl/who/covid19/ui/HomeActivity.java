package intl.who.covid19.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;

import intl.who.covid19.Api;
import intl.who.covid19.App;
import intl.who.covid19.BeaconService;
import intl.who.covid19.LocalNotificationReceiver;
import intl.who.covid19.Prefs;
import intl.who.covid19.R;
import intl.who.covid19.UploadService;
import sk.turn.http.Http;

public class HomeActivity extends AppCompatActivity {
	/** boolean Whether to ask the user immediately if he's coming from abroad */
	public static final String EXTRA_ASK_QUARANTINE = "intl.who.covid19.EXTRA_CHECK_QUARANTINE";
	private static final int REQUEST_QUARANTINE_START = 1;
	private static final int REQUEST_ADDRESS = 2;
	private static final int REQUEST_PHONE_VERIFICATION = 3;

	private View layout_quarantine;
	private TextView textView_address;
	private TextView textView_quarantineDaysLeft;
	private View layout_info;
	private TextView textView_statsTotal;
	private TextView textView_statsRecovered;
	private TextView textView_statsDeaths;
	private Button button_report;
	private Button button_hotline;
	private String hotline;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);
		layout_quarantine = findViewById(R.id.layout_quarantine);
		textView_address = findViewById(R.id.textView_address);
		textView_quarantineDaysLeft = findViewById(R.id.textView_quarantineDaysLeft);
		layout_info = findViewById(R.id.layout_info);
		textView_statsTotal = findViewById(R.id.textView_statsTotal);
		textView_statsRecovered = findViewById(R.id.textView_statsRecovered);
		textView_statsDeaths = findViewById(R.id.textView_statsDeaths);
		button_report = findViewById(R.id.button_report);
		button_hotline = findViewById(R.id.button_hotline);
		startService(new Intent(this, BeaconService.class));
		UploadService.start(this);
		if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_ASK_QUARANTINE, false)) {
			new ConfirmDialog(this, getString(R.string.home_checkReport))
					.setButton1(getString(R.string.app_yes), R.drawable.bg_btn_red, v -> onButtonReport(null))
					.setButton2(getString(R.string.app_no), R.drawable.bg_btn_green, null)
					.show();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateUi();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		if (requestCode == REQUEST_QUARANTINE_START && resultCode == RESULT_OK && data != null) {
			startActivityForResult(new Intent(this, AddressActivity.class)
					.putExtras(data), REQUEST_ADDRESS);
		} else if (requestCode == REQUEST_ADDRESS && resultCode == RESULT_OK && data != null) {
			startActivityForResult(new Intent(this, PhoneVerificationActivity.class)
					.putExtras(data), REQUEST_PHONE_VERIFICATION);
		} else if (requestCode == REQUEST_PHONE_VERIFICATION && resultCode == RESULT_OK && data != null) {
			// Save place and phone number
			App.get(this).prefs().edit()
					.putString(Prefs.HOME_ADDRESS, data.getStringExtra(AddressActivity.EXTRA_ADDRESS))
					.putFloat(Prefs.HOME_LAT, (float) data.getDoubleExtra(AddressActivity.EXTRA_LAT, 0))
					.putFloat(Prefs.HOME_LNG, (float) data.getDoubleExtra(AddressActivity.EXTRA_LNG, 0))
					.putString(Prefs.PHONE_NUMBER, data.getStringExtra(Intent.EXTRA_PHONE_NUMBER))
					.apply();
			if (!App.get(this).isInQuarantine()) {
				int duration = (int) App.get(this).getRemoteConfig().getDouble(App.RC_QUARANTINE_DURATION);
				App.get(this).prefs().edit().putLong(Prefs.QUARANTINE_ENDS,
						data.getLongExtra(QuarantineStartActivity.EXTRA_QUARANTINE_START, System.currentTimeMillis()) +
								duration * 24 * 3_600_000L).apply();
				// Restart service to switch to quarantine location tracking
				startService(new Intent(this, BeaconService.class));
				LocalNotificationReceiver.scheduleNotification(this);
			}
			updateUi();
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	public void onButtonProtect(View v) {
		startActivity(new Intent(this, ProtectActivity.class));
	}

	public void onButtonSymptoms(View v) {
		startActivity(new Intent(this, SymptomsActivity.class));
	}

	public void onButtonReport(View v) {
		startActivityForResult(new Intent(this, QuarantineStartActivity.class), REQUEST_QUARANTINE_START);
	}

	public void onButtonHotline(View v) {
		startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + hotline)));
	}

	public void onPrivacyPolicy(View view) {
		startActivity(new Intent(this, PrivacyPolicyActivity.class));
	}

	private void updateUi() {
		App app = App.get(this);
		if (app.isInQuarantine()) {
			layout_quarantine.setVisibility(View.VISIBLE);
			layout_info.setVisibility(View.GONE);
			button_report.setVisibility(View.GONE);
			textView_address.setText(app.prefs().getString(Prefs.HOME_ADDRESS, ""));
			textView_quarantineDaysLeft.setText(String.valueOf(app.getDaysLeftInQuarantine()));
		} else {
			layout_quarantine.setVisibility(View.GONE);
			layout_info.setVisibility(View.VISIBLE);
			button_report.setVisibility(View.VISIBLE);
			updateStats();
		}
		// Try to load the hotline number for current country
		String hotlinesJson = app.getRemoteConfig().getString(App.RC_HOTLINES);
		HashMap<String, String> hotlines = new Gson().fromJson(hotlinesJson, new TypeToken<HashMap<String, String>>() { }.getType());
		hotline = hotlines.get(app.prefs().getString(Prefs.COUNTRY_CODE, ""));
		button_hotline.setVisibility(hotline != null && hotline.length() > 0 ? View.VISIBLE : View.GONE);
	}

	private void updateStats() {
		Api.Stats stats = Api.Stats.fromJson(App.get(this).prefs().getString(Prefs.STATS, "null"));
		textView_statsTotal.setText(stats == null ? "..." : String.valueOf(stats.totalCases));
		textView_statsRecovered.setText(stats == null ? "..." : String.valueOf(stats.totalRecovered));
		textView_statsDeaths.setText(stats == null ? "..." : String.valueOf(stats.totalDeaths));
		// Update stats if necessary
		if (stats == null || System.currentTimeMillis() - stats.lastUpdate > 3_600_000L) {
			String statsUrl = App.get(this).getRemoteConfig().getString(App.RC_STATS_URL);
			if (statsUrl.isEmpty()) {
				return;
			}
			new Http(statsUrl, Http.GET).send(http -> {
				if (isFinishing() || http.getResponseCode() != 200) {
					return;
				}
				Api.Stats newStats = Api.Stats.fromJson(http.getResponseString());
				newStats.lastUpdate = System.currentTimeMillis();
				textView_statsTotal.post(() -> {
					App.get(HomeActivity.this).prefs().edit().putString(Prefs.STATS, newStats.toJson()).apply();
					updateStats();
				});
			});
		}
	}
}
