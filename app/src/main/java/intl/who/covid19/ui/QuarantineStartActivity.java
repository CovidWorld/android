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

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.text.DateFormat;
import java.util.Calendar;

import intl.who.covid19.App;
import intl.who.covid19.R;

public class QuarantineStartActivity extends AppCompatActivity {
    public static final String EXTRA_QUARANTINE_START = "intl.who.covid19.ui.EXTRA_QUARANTINE_START";

    private long startTime = System.currentTimeMillis();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quarantine_start);
        ((TextView) findViewById(R.id.textView_date)).setText(DateFormat.getDateInstance(DateFormat.MEDIUM).format(startTime));
        DatePicker datePicker = findViewById(R.id.datePicker);
        Calendar cal = Calendar.getInstance();
        datePicker.setMinDate(System.currentTimeMillis() - ((int) App.get(this).getRemoteConfig().getDouble(App.RC_QUARANTINE_DURATION) - 1) * 24 * 3_600_000L);
        datePicker.setMaxDate(System.currentTimeMillis());
        datePicker.init(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), (view, year, monthOfYear, dayOfMonth) -> {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, monthOfYear);
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            findViewById(R.id.layout_datePicker).setVisibility(View.GONE);
            startTime = cal.getTimeInMillis();
            ((TextView) findViewById(R.id.textView_date)).setText(DateFormat.getDateInstance(DateFormat.MEDIUM).format(startTime));
        });
    }

    @Override
    public void onBackPressed() {
        if (findViewById(R.id.layout_datePicker).getVisibility() == View.VISIBLE) {
            findViewById(R.id.layout_datePicker).setVisibility(View.GONE);
        } else {
            super.onBackPressed();
        }
    }

    public void onPickDate(View view) {
        findViewById(R.id.layout_datePicker).setVisibility(View.VISIBLE);
    }

    public void onButtonContinue(View view) {
        setResult(RESULT_OK, new Intent().putExtras(getIntent()).putExtra(EXTRA_QUARANTINE_START, startTime));
        finish();
    }
}
