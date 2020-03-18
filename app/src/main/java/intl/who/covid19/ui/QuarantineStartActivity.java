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
