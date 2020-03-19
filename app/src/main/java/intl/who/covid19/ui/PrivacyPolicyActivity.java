package intl.who.covid19.ui;

import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;

import intl.who.covid19.R;

public class PrivacyPolicyActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);
        try (InputStream inputStream = getResources().openRawResource(R.raw.privacy)) {
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            TextView text = findViewById(R.id.textView_text);
            text.setText(Html.fromHtml(new String(buffer)));
        } catch (IOException e) {
            // Well, not much to do here...
        }
    }
}
