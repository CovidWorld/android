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

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

public class LocationQueue extends DataQueue<Location> {

    private static final Type LIST_TYPE = new TypeToken<List<Location>>(){}.getType();

    public LocationQueue(Context context) {
        super(context, "locations.json");
    }

    @Override
    protected Type getListType() {
        return LIST_TYPE;
    }

    @Override
    protected void makeSendRequest(List<Location> data, Api.Listener listener) {
        final Api.LocationRequest request = new Api.LocationRequest(
                App.get(context).prefs().getString(Prefs.DEVICE_UID, null),
                App.get(context).prefs().getLong(Prefs.DEVICE_ID, 0));
        request.locations.addAll(data);
        new Api(context).sendLocations(request, listener);
    }

}
