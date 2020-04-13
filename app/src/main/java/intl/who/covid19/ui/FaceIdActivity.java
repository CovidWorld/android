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
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;

import com.innovatrics.android.dot.Dot;
import com.innovatrics.android.dot.dto.FaceCaptureArguments;
import com.innovatrics.android.dot.dto.Photo;
import com.innovatrics.android.dot.face.DetectedFace;
import com.innovatrics.android.dot.facecapture.steps.CaptureState;
import com.innovatrics.android.dot.utils.LicenseUtils;
import com.innovatrics.android.dot.verification.TemplateVerifier;

import intl.who.covid19.App;
import intl.who.covid19.Prefs;
import intl.who.covid19.R;

public class FaceIdActivity extends AppCompatActivity implements Dot.Listener {
    /** Whether to show the registration/learning flow (false/missing) or verification flow (true) */
    public static final String EXTRA_LEARN = "intl.who.covid19.EXTRA_LEARN";

    public static class FaceCaptureFragment extends com.innovatrics.android.dot.fragment.FaceCaptureFragment {
        @Override
        protected void onCameraInitFailed() {
            onFailed(R.string.faceid_failCameraInit, true);
        }
        @Override
        protected void onCameraAccessFailed() {
            onFailed(R.string.faceid_failCameraAccess, false);
        }
        @Override
        protected void onNoCameraPermission() {
            onFailed(R.string.faceid_failCameraPermission, true);
        }
        @Override
        protected void onCaptureStateChange(CaptureState captureState, Photo photo) { }
        @Override
        protected void onCaptureSuccess(DetectedFace detectedFace) {
            Activity activity = getActivity();
            if (activity instanceof FaceIdActivity) {
                ((FaceIdActivity) activity).onFaceDetected(detectedFace);
            }
        }
        @Override
        protected void onCaptureFail() {
            onFailed(R.string.faceid_failCapture, true);
        }
        private void onFailed(@StringRes int error, boolean retry) {
            Activity activity = getActivity();
            if (activity instanceof FaceIdActivity) {
                ((FaceIdActivity) activity).showResult(false, getString(error), retry);
            }
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_faceid);
        if (!Dot.getInstance().isInitialized()) {
            int licenseResId = getResources().getIdentifier("innovatrics_license", "raw", getPackageName());
            if (licenseResId == 0) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.app_name)
                        .setMessage("Missing innovatrics license file in raw/innovatrics_license")
                        .setPositiveButton(R.string.app_ok, (d, w) -> finish())
                        .show();
            } else {
                Dot.getInstance().initAsync(LicenseUtils.loadRawLicense(this, licenseResId), this,
                        (float) App.get(this).getRemoteConfig().getDouble(App.RC_FACEID_CONFIDENCE_THRESHOLD));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Dot.getInstance().closeAsync(this);
    }

    @Override
    public void onInitSuccess() {
        getWindow().getDecorView().post(() -> {
            findViewById(R.id.progressBar).setVisibility(View.GONE);
            if (isLearning()) {
                findViewById(R.id.layout_intro).setVisibility(View.VISIBLE);
            } else {
                showFaceDetectionFragment();
            }
        });
    }

    @Override
    public void onInitFail(final String message) {
        getWindow().getDecorView().post(() -> {
            new AlertDialog.Builder(FaceIdActivity.this)
                    .setTitle(R.string.app_name)
                    .setMessage("Invalid innovatrics license: " + message)
                    .setPositiveButton(R.string.app_ok, (d, w) -> finish())
                    .show();
        });
    }

    @Override
    public void onClosed() {
    }

    public void onButtonStart(View v) {
        findViewById(R.id.layout_intro).setVisibility(View.GONE);
        showFaceDetectionFragment();
    }

    private void showFaceDetectionFragment() {
        final Bundle arguments = new Bundle();
        Fragment fragment = new FaceCaptureFragment();
        arguments.putSerializable(FaceCaptureFragment.ARGUMENTS, new FaceCaptureArguments.Builder()
                .lightScoreThreshold(.4)
                .build());
        fragment.setArguments(arguments);
        getSupportFragmentManager().beginTransaction().replace(R.id.layout_container, fragment).commit();
    }

    private void removeFaceDetectionFragment() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            getSupportFragmentManager().beginTransaction().remove(fragment).commit();
        }
    }

    private void onFaceDetected(DetectedFace face) {
        if (isLearning()) {
            // Save detected face template data
            App.get(this).prefs().edit().putString(Prefs.FACE_TEMPLATE_DATA, bytesToHex(face.createTemplate().getTemplate())).apply();
            showResult(true, getString(R.string.faceid_success), false);
        } else {
            // Compare with saved face template
            byte[] savedTemplate = hexToBytes(App.get(this).prefs().getString(Prefs.FACE_TEMPLATE_DATA, ""));
            try {
                float score = new TemplateVerifier().match(savedTemplate, face.createTemplate().getTemplate());
                if (score >= App.get(this).getRemoteConfig().getDouble(App.RC_FACEID_MATCH_THRESHOLD)) {
                    showResult(true, getString(R.string.faceid_success), false);
                } else {
                    showResult(false, getString(R.string.faceid_failVerify), true);
                }
            } catch (Exception e) {
                showResult(false, e.getMessage(), true);
            }
        }
    }

    private void showResult(boolean success, String message, boolean retry) {
        removeFaceDetectionFragment();
        findViewById(R.id.layout_result).setVisibility(View.VISIBLE);
        this.<AppCompatImageView>findViewById(R.id.imageView_result).setImageResource(success ? R.drawable.ic_check_green : R.drawable.ic_check_red);
        this.<TextView>findViewById(R.id.textView_result_title).setText(success ? R.string.faceid_thankYou : R.string.faceid_sorry);
        this.<TextView>findViewById(R.id.textView_result_text).setText(message);
        findViewById(R.id.button_continue).setOnClickListener(v -> {
            if (success) {
                setResult(RESULT_OK);
                finish();
            } else if (retry) {
                findViewById(R.id.layout_result).setVisibility(View.GONE);
                showFaceDetectionFragment();
            } else {
                finish();
            }
        });
    }

    private boolean isLearning() {
        return getIntent().getBooleanExtra(EXTRA_LEARN, false);
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
    private static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for(int i = 0; i < len; i += 2){
            data[i/2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
