package intl.who.covid19.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;

import intl.who.covid19.App;
import intl.who.covid19.BeaconService;
import intl.who.covid19.Prefs;
import intl.who.covid19.R;
import intl.who.covid19.UploadService;

public class HomeActivity extends AppCompatActivity {
	/** boolean Whether to ask the user immediately if he's coming from abroad */
	public static final String EXTRA_ASK_QUARANTINE = "intl.who.covid19.EXTRA_CHECK_QUARANTINE";
	private static final int REQUEST_QUARANTINE_START = 1;
	private static final int REQUEST_ADDRESS = 2;
	private static final int REQUEST_PHONE_VERIFICATION = 3;

	private String hotline;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);
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

	private void updateUi() {
		App app = App.get(this);
		if (app.isInQuarantine()) {
			findViewById(R.id.layout_quarantine).setVisibility(View.VISIBLE);
			findViewById(R.id.textView_quarantineInfo).setVisibility(View.GONE);
			findViewById(R.id.button_report).setVisibility(View.GONE);
			this.<TextView>findViewById(R.id.textView_address).setText(app.prefs().getString(Prefs.HOME_ADDRESS, ""));
			this.<TextView>findViewById(R.id.textView_quarantineDaysLeft).setText(String.valueOf((int) Math.ceil(
					(app.prefs().getLong(Prefs.QUARANTINE_ENDS, 0L) - System.currentTimeMillis()) / (24.0 * 3_600_000L))));
		} else {
			findViewById(R.id.layout_quarantine).setVisibility(View.GONE);
			findViewById(R.id.textView_quarantineInfo).setVisibility(View.VISIBLE);
			findViewById(R.id.button_report).setVisibility(View.VISIBLE);
		}
		// Try to load the hotline number for current country
		String hotlinesJson = app.getRemoteConfig().getString(App.RC_HOTLINES);
		HashMap<String, String> hotlines = new Gson().fromJson(hotlinesJson, new TypeToken<HashMap<String, String>>() { }.getType());
		hotline = hotlines.get(app.prefs().getString(Prefs.COUNTRY_CODE, ""));
		findViewById(R.id.button_hotline).setVisibility(hotline != null && hotline.length() > 0 ? View.VISIBLE : View.GONE);
	}
}
