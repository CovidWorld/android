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
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;

import intl.who.covid19.App;
import intl.who.covid19.BeaconService;
import intl.who.covid19.CountryDefaults;
import intl.who.covid19.LocalNotificationReceiver;
import intl.who.covid19.Prefs;
import intl.who.covid19.R;

public class HomeFragment extends Fragment {
    public static class Stats {
        public int positive;
        public int recovered;
        public int deaths;
    }

    private static final int REQUEST_QUARANTINE_START = 1;
    private static final int REQUEST_ADDRESS = 2;
    private static final int REQUEST_PHONE_VERIFICATION = 3;

    private View layout_quarantine;
    private TextView textView_address;
    private TextView textView_quarantineDaysLeft;
    private TextView textView_statsTotal;
    private TextView textView_statsRecovered;
    private TextView textView_statsDeaths;
    private Button button_quarantine;
    private Button button_hotline;
    private String hotline;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        layout_quarantine = view.findViewById(R.id.layout_quarantine);
        textView_address = view.findViewById(R.id.textView_address);
        textView_quarantineDaysLeft = view.findViewById(R.id.textView_quarantineDaysLeft);
        textView_statsTotal = view.findViewById(R.id.textView_statsTotal);
        textView_statsRecovered = view.findViewById(R.id.textView_statsRecovered);
        textView_statsDeaths = view.findViewById(R.id.textView_statsDeaths);
        button_quarantine = view.findViewById(R.id.button_quarantine);
        button_hotline = view.findViewById(R.id.button_hotline);
        view.findViewById(R.id.button_info).setOnClickListener(v -> startActivity(new Intent(view.getContext(), InfoActivity.class)));
        button_quarantine.setOnClickListener(v -> onButtonQuarantine());
        button_hotline.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + hotline))));
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUi();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        App app = App.get(context);
        if (requestCode == REQUEST_QUARANTINE_START && resultCode == Activity.RESULT_OK && data != null) {
            startActivityForResult(new Intent(context, AddressActivity.class)
                    .putExtras(data), REQUEST_ADDRESS);
        } else if (requestCode == REQUEST_ADDRESS && resultCode == Activity.RESULT_OK && data != null) {
            startActivityForResult(new Intent(context, PhoneVerificationActivity.class)
                    .putExtras(data), REQUEST_PHONE_VERIFICATION);
        } else if (requestCode == REQUEST_PHONE_VERIFICATION && resultCode == Activity.RESULT_OK && data != null) {
            // Save place and phone number
            app.prefs().edit()
                    .putString(Prefs.HOME_ADDRESS, data.getStringExtra(AddressActivity.EXTRA_ADDRESS))
                    .putFloat(Prefs.HOME_LAT, (float) data.getDoubleExtra(AddressActivity.EXTRA_LAT, 0))
                    .putFloat(Prefs.HOME_LNG, (float) data.getDoubleExtra(AddressActivity.EXTRA_LNG, 0))
                    .apply();
            if (!app.isInQuarantine()) {
                int duration = (int) app.getRemoteConfig().getDouble(App.RC_QUARANTINE_DURATION);
                app.prefs().edit().putLong(Prefs.QUARANTINE_ENDS,
                        data.getLongExtra(QuarantineStartActivity.EXTRA_QUARANTINE_START, System.currentTimeMillis()) +
                                duration * 24 * 3_600_000L).apply();
                // Restart service to switch to quarantine location tracking
                context.startService(new Intent(context, BeaconService.class));
                LocalNotificationReceiver.scheduleNotification(context);
            }
            updateUi();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    void onButtonQuarantine() {
        startActivityForResult(new Intent(getContext(), QuarantineStartActivity.class), REQUEST_QUARANTINE_START);
    }

    private void updateUi() {
        App app = App.get(getContext());
        if (app.isInQuarantine()) {
            layout_quarantine.setVisibility(View.VISIBLE);
            textView_address.setText(app.prefs().getString(Prefs.HOME_ADDRESS, ""));
            textView_quarantineDaysLeft.setText(String.valueOf(app.getDaysLeftInQuarantine()));
            button_quarantine.setVisibility(View.GONE);
        } else {
            layout_quarantine.setVisibility(View.GONE);
            button_quarantine.setVisibility(View.VISIBLE);
        }
        reloadStats();
        // Try to load the hotline number for current country
        String hotlinesJson = app.getRemoteConfig().getString(App.RC_HOTLINES);
        HashMap<String, String> hotlines = new Gson().fromJson(hotlinesJson, new TypeToken<HashMap<String, String>>() { }.getType());
        hotline = hotlines.get(app.prefs().getString(Prefs.COUNTRY_CODE, ""));
        button_hotline.setVisibility(hotline != null && hotline.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private void reloadStats() {
        CountryDefaults.getStats(getContext(), stats -> {
            if (getActivity() == null || getActivity().isFinishing()) {
                return;
            }
            textView_statsTotal.setText(stats == null ? "..." : String.valueOf(stats.positive));
            textView_statsRecovered.setText(stats == null ? "..." : String.valueOf(stats.recovered));
            textView_statsDeaths.setText(stats == null ? "..." : String.valueOf(stats.deaths));
        });
    }
}
