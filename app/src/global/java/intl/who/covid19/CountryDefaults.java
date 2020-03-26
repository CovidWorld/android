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

public class CountryDefaults {
    public static final double CENTER_LAT = 49;
    public static final double CENTER_LNG = 17;
    public static final int CENTER_ZOOM = 4;

    public static void getStats(Context context, App.Callback<HomeFragment.Stats> callback) {
        callback.onCallback(null);
    }

    public static void getCountyStats(Context context, App.Callback<List<MapFragment.CountyStats>> callback) {
        callback.onCallback(new ArrayList<>());
    }
}
