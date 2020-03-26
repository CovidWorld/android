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
