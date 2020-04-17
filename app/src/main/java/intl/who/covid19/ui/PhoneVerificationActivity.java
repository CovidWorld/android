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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.github.ialokim.phonefield.PhoneInputLayout;

import java.io.IOException;

import intl.who.covid19.Api;
import intl.who.covid19.App;
import intl.who.covid19.Prefs;
import intl.who.covid19.R;

public class PhoneVerificationActivity extends AppCompatActivity {
	public static class QuarantineDetails {
		public String covidId;
		public String quarantineStart;
		public String quarantineEnd;
	}

	public static final String EXTRA_SHOW_EXPLANATION = "intl.who.covid19.ui.EXTRA_SHOW_EXPLANATION";

	private PhoneInputLayout phoneInput;
	private EditText editTextCode;
	private CheckBox checkBoxPrivacyConsent;
	private Button buttonDone;
	private ProgressBar progressBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_phone_verification);

		phoneInput = findViewById(R.id.phoneInput);
		editTextCode = findViewById(R.id.editText_code);
		checkBoxPrivacyConsent = findViewById(R.id.checkBox_privacyConsent);
		buttonDone = findViewById(R.id.button_done);
		progressBar = findViewById(R.id.progressBar);

		phoneInput.setDefaultCountry(App.get(this).getCountryDefaults().getCountryCode());
		editTextCode.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) { }
			@Override
			public void afterTextChanged(Editable s) {
				if (s.length() == App.get(PhoneVerificationActivity.this).getCountryDefaults().getVerificationCodeLength()) {
					hideKeyboard();
					confirmVerificationCode(s.toString());
				}
			}
		});
		((TextView) findViewById(R.id.textView_text)).setText(getIntent().getBooleanExtra(EXTRA_SHOW_EXPLANATION, false) ?
				R.string.phoneVerification_explanation : R.string.phoneVerification_text);
		// Check if we have the phone number and verification code stored and confirm immediately if so
		String verificationCode = App.get(this).prefs().getString(Prefs.PHONE_NUMBER_VERIFICATION_CODE, null);
		if (verificationCode != null) {
			confirmVerificationCode(verificationCode);
		}
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

	public void onPrivacy(View v) {
		startActivity(new Intent(this, PrivacyPolicyActivity.class));
	}

	public void onButtonDone(View view) {
		// checks if the field is valid
		if (!phoneInput.isValid()) {
			phoneInput.setError(getString(R.string.phoneVerification_invalidNumber));
		} else if (!checkBoxPrivacyConsent.isChecked()) {
			phoneInput.setError(null);
			new AlertDialog.Builder(this)
					.setTitle(R.string.app_name)
					.setMessage(R.string.phoneVerification_notAgreed)
					.setPositiveButton(R.string.app_ok, null)
					.show();
		} else {
			phoneInput.setError(null);
			hideKeyboard();
			showVerificationDialog(phoneInput.getPhoneNumberE164());
		}
	}

	private void showVerificationDialog(String phoneNumber) {
		// verify phone number by user
		new AlertDialog.Builder(this)
				.setTitle(R.string.phoneVerification_confirmTitle)
				.setMessage(getString(R.string.phoneVerification_confirmText, phoneNumber))
				.setPositiveButton(R.string.app_yes, (dialog, which) -> requestVerificationCode(phoneNumber))
				.setNegativeButton(R.string.app_no, (dialog, which) -> phoneInput.getEditText().selectAll())
				.show();
	}

	private void requestVerificationCode(String phoneNumber) {
		progressBar.setVisibility(View.VISIBLE);
		buttonDone.setVisibility(View.GONE);
		SharedPreferences prefs = App.get(this).prefs();
		App.get(this).getCountryDefaults().sendVerificationCodeText(this, phoneNumber, exception -> {
			if (isFinishing()) {
				return;
			}
			progressBar.setVisibility(View.GONE);
			if (exception != null) {
				buttonDone.setVisibility(View.VISIBLE);
				new AlertDialog.Builder(this)
						.setTitle(R.string.app_name)
						.setMessage(exception instanceof IOException ? getString(R.string.app_apiFailed, exception.getMessage()) : exception.getMessage())
						.setPositiveButton(android.R.string.ok, null)
						.show();
			} else {
				// Update the text and show code input instead of phone number input
				this.<TextView>findViewById(R.id.textView_text).setText(R.string.phoneVerification_enterCode);
				phoneInput.setVisibility(View.GONE);
				checkBoxPrivacyConsent.setVisibility(View.GONE);
				editTextCode.setVisibility(View.VISIBLE);
			}
		});
	}

	private void confirmVerificationCode(String code) {
		progressBar.setVisibility(View.VISIBLE);
		editTextCode.setEnabled(false);
		App.get(this).getCountryDefaults().checkVerificationCode(this, phoneInput.getPhoneNumberE164(), code, quarantineDetails -> {
			if (isFinishing()) {
				return;
			}
			if (quarantineDetails == null) {
				editTextCode.setText("");
				progressBar.setVisibility(View.GONE);
				editTextCode.setEnabled(true);
				new AlertDialog.Builder(this)
						.setTitle(R.string.app_name)
						.setMessage(getString(R.string.phoneVerification_wrongCode))
						.setPositiveButton(android.R.string.ok, null)
						.show();
			} else {
				long qEnd = App.setEndOfDay(App.parseIsoDate(quarantineDetails.quarantineEnd));
				App.get(this).prefs().edit()
						.putString(Prefs.PHONE_NUMBER, phoneInput.getPhoneNumberE164())
						.putString(Prefs.PHONE_NUMBER_VERIFICATION_CODE, code)
						.putString(Prefs.COVID_ID, quarantineDetails.covidId)
						.putLong(Prefs.QUARANTINE_ENDS, qEnd)
						.apply();
				// Send the data to our API
				new Api(PhoneVerificationActivity.this).confirmQuarantine(quarantineDetails.covidId, quarantineDetails.quarantineStart,
						quarantineDetails.quarantineEnd, (status, response) -> {
							// Do we really care if this fails?
							setResult(RESULT_OK, new Intent().putExtras(getIntent()));
							finish();
						});
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
