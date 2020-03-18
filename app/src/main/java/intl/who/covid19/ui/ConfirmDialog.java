package intl.who.covid19.ui;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import intl.who.covid19.R;

public class ConfirmDialog extends BottomSheetDialog {
    public ConfirmDialog(Context context, String text) {
        super(context);
        setContentView(R.layout.dialog_confirm);
        this.<TextView>findViewById(R.id.textView_text).setText(text);
    }

    public ConfirmDialog setButton1(String text, @DrawableRes int background, @Nullable View.OnClickListener onClick) {
        setButton(R.id.button1, text, background, onClick);
        return this;
    }

    public ConfirmDialog setButton2(String text, @DrawableRes int background, @Nullable View.OnClickListener onClick) {
        setButton(R.id.button2, text, background, onClick);
        return this;
    }

    private void setButton(int id, String text, @DrawableRes int background, @Nullable View.OnClickListener onClick) {
        Button button = findViewById(id);
        if (button == null) {
            return;
        }
        button.setText(text);
        button.setBackgroundResource(background);
        button.setOnClickListener(v -> {
            dismiss();
            if (onClick != null) {
                onClick.onClick(v);
            }
        });
        button.setVisibility(View.VISIBLE);
    }
}
