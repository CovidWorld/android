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

package intl.who.covid19;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import intl.who.covid19.ui.HomeFragment;
import intl.who.covid19.ui.MapFragment;
import intl.who.covid19.ui.PhoneVerificationActivity;

public class CountryDefaults implements ICountryDefaults {
    @Override
    public boolean verifyPhoneNumberAtStart() { return false; }
    @Override
    public boolean showQuarantineStartPicker() { return false; }
    @Override
    public boolean useFaceId() { return false; }
    @Override
    public String getCountryCode() { return "XX"; }
    @Override
    public double getCenterLat() { return 49; }
    @Override
    public double getCenterLng() { return 17; }
    @Override
    public double getCenterZoom() { return 4; }

    @Override
    public void getStats(Context context, App.Callback<HomeFragment.Stats> callback) {
        callback.onCallback(null);
    }

    @Override
    public void getCountyStats(Context context, App.Callback<List<MapFragment.CountyStats>> callback) {
        callback.onCallback(new ArrayList<>());
    }

    @Override
    public void sendVerificationCodeText(Context context, String phoneNumber, App.Callback<Exception> callback) {
        callback.onCallback(new UnsupportedOperationException("This operation is not supported or not implemented in this flavor"));
    }

    @Override
    public int getVerificationCodeLength() { return 4; }

    @Override
    public void checkVerificationCode(Context context, String phoneNumber, String code, App.Callback<PhoneVerificationActivity.QuarantineDetails> callback) {
        callback.onCallback(null);
    }
}
