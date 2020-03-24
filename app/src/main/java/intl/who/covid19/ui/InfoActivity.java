package intl.who.covid19.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import intl.who.covid19.R;

public class InfoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);
    }

    public void onButtonProtect(View v) {
        startActivity(new Intent(this, ProtectActivity.class));
    }

    public void onButtonSymptoms(View v) {
        startActivity(new Intent(this, SymptomsActivity.class));
    }

    public void onButtonAbout(View view) {
        startActivity(new Intent(view.getContext(), AboutActivity.class));
    }

    public void onPrivacyPolicy(View view) {
        startActivity(new Intent(view.getContext(), PrivacyPolicyActivity.class));
    }
}
