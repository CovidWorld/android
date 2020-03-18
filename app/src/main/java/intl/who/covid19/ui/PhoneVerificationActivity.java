package intl.who.covid19.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.github.ialokim.phonefield.PhoneInputLayout;

import intl.who.covid19.Api;
import intl.who.covid19.App;
import intl.who.covid19.Prefs;
import intl.who.covid19.R;

public class PhoneVerificationActivity extends AppCompatActivity {

	private static final int CODE_LENGTH = 6;

	private PhoneInputLayout phoneInput;
	private EditText editTextCode;
	private Button buttonDone;
	private ProgressBar progressBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_phone_verification);

		phoneInput = findViewById(R.id.phoneInput);
		editTextCode = findViewById(R.id.editText_code);
		buttonDone = findViewById(R.id.button_done);
		progressBar = findViewById(R.id.progressBar);

		phoneInput.setDefaultCountry(App.get(this).prefs().getString(Prefs.COUNTRY_CODE, ""));
		editTextCode.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) { }
			@Override
			public void afterTextChanged(Editable s) {
				if (s.length() == CODE_LENGTH) {
					hideKeyboard();
					confirmVerificationCode(s.toString());
				}
			}
		});
	}

	@Override
	public void onBackPressed() {
		if (editTextCode.getVisibility() == View.VISIBLE) {
			new AlertDialog.Builder(this)
					.setTitle(R.string.phoneVerification_title)
					.setMessage(R.string.phoneVerification_leaveProcess)
					.setPositiveButton(R.string.app_yes, (dialog, which) -> super.onBackPressed())
					.setNegativeButton(R.string.app_no, null)
					.show();
		} else {
			super.onBackPressed();
		}
	}

	public void onButtonDone(View view) {
		// checks if the field is valid
		if (phoneInput.isValid()) {
			phoneInput.setError(null);
			hideKeyboard();
			showVerificationDialog(phoneInput.getPhoneNumberE164());
		} else {
			phoneInput.setError(getString(R.string.phoneVerification_invalidNumber));
		}
	}

	private void showVerificationDialog(String phoneNumber) {
		// verify phone number by user
		new AlertDialog.Builder(this)
				.setTitle(R.string.phoneVerification_confirmTitle)
				.setMessage(getString(R.string.phoneVerification_confirmText, phoneNumber))
				.setPositiveButton(R.string.app_yes, (dialog, which) -> savePhoneNumber(phoneNumber))
				.setNegativeButton(R.string.app_no, (dialog, which) -> phoneInput.getEditText().selectAll())
				.show();
	}

	private void savePhoneNumber(String phoneNumber) {
		progressBar.setVisibility(View.VISIBLE);
		buttonDone.setVisibility(View.GONE);
		SharedPreferences prefs = App.get(this).prefs();
		Api.ProfileRequest req = new Api.ProfileRequest(
				prefs.getString(Prefs.DEVICE_UID, null),
				prefs.getString(Prefs.FCM_TOKEN, null),
				phoneNumber);
		new Api(this).createProfile(req, (status, response) -> {
			if (isFinishing()) {
				return;
			}
			progressBar.setVisibility(View.GONE);
			if (status != 200) {
				buttonDone.setVisibility(View.VISIBLE);
				new AlertDialog.Builder(this)
						.setTitle(R.string.app_name)
						.setMessage(getString(R.string.app_apiFailed, (status + " " + response).trim()))
						.setPositiveButton(android.R.string.ok, null)
						.show();
			} else {
				requestVerificationCode();
			}
		});
	}

	private void requestVerificationCode() {
		progressBar.setVisibility(View.VISIBLE);
		buttonDone.setVisibility(View.GONE);
		SharedPreferences prefs = App.get(this).prefs();
		Api.AuthTokenRequest req = new Api.AuthTokenRequest(
				prefs.getString(Prefs.DEVICE_UID, null),
				prefs.getLong(Prefs.DEVICE_ID, 0));
		new Api(this).requestAuthToken(req, (status, response) -> {
			if (isFinishing()) {
				return;
			}
			progressBar.setVisibility(View.GONE);
			if (status != 200) {
				buttonDone.setVisibility(View.VISIBLE);
				new AlertDialog.Builder(this)
						.setTitle(R.string.app_name)
						.setMessage(getString(R.string.app_apiFailed, (status + " " + response).trim()))
						.setPositiveButton(android.R.string.ok, null)
						.show();
			} else {
				// Update the text and show code input instead of phone number input
				this.<TextView>findViewById(R.id.textView_text).setText(R.string.phoneVerification_enterCode);
				phoneInput.setVisibility(View.GONE);
				editTextCode.setVisibility(View.VISIBLE);
			}
		});
	}

	private void confirmVerificationCode(String code) {
		progressBar.setVisibility(View.VISIBLE);
		editTextCode.setEnabled(false);
		SharedPreferences prefs = App.get(this).prefs();
		long quarantineStarts = getIntent().getLongExtra(QuarantineStartActivity.EXTRA_QUARANTINE_START, System.currentTimeMillis());
		long daysLeftInQuarantine = (int) App.get(this).getRemoteConfig().getDouble(App.RC_QUARANTINE_DURATION) -
				Math.round((System.currentTimeMillis() - quarantineStarts) / (24 * 3_600_000.0));
		Api.ConfirmQuarantineRequest req = new Api.ConfirmQuarantineRequest(
				prefs.getString(Prefs.DEVICE_UID, null),
				prefs.getLong(Prefs.DEVICE_ID, 0),
				(int) daysLeftInQuarantine,
				code);
		new Api(this).confirmQuarantine(req, (status, response) -> {
			if (isFinishing()) {
				return;
			}
			if (status != 200) {
				editTextCode.setText("");
				progressBar.setVisibility(View.GONE);
				editTextCode.setEnabled(true);
				new AlertDialog.Builder(this)
						.setTitle(R.string.app_name)
						.setMessage(getString(R.string.phoneVerification_wrongCode))
						.setPositiveButton(android.R.string.ok, null)
						.show();
			} else {
				setResult(RESULT_OK, new Intent().putExtras(getIntent()).putExtra(Intent.EXTRA_PHONE_NUMBER, phoneInput.getPhoneNumberE164()));
				finish();
			}
		});
	}

	private void hideKeyboard() {
		InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
		if (imm != null) {
			imm.hideSoftInputFromWindow(phoneInput.getEditText().getWindowToken(), 0);
		}
	}
}
