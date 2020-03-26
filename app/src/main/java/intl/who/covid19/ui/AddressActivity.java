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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.TypeFilter;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;

import java.util.Arrays;
import java.util.List;

import intl.who.covid19.App;
import intl.who.covid19.Prefs;
import intl.who.covid19.R;

public class AddressActivity extends AppCompatActivity implements OnMapReadyCallback {
    public static final String EXTRA_ADDRESS = "intl.who.covid19.EXTRA_ADDRESS";
    public static final String EXTRA_LAT = "intl.who.covid19.EXTRA_LAT";
    public static final String EXTRA_LNG = "intl.who.covid19.EXTRA_LNG";
    private static final int DEFAULT_ZOOM = 17;
    private static final int REQUEST_AUTOCOMPLETE = 1;

    private GoogleMap map;
    private String address;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address);
        ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_map)).getMapAsync(this);
        new ConfirmDialog(this, getString(R.string.address_info))
                .setButton1(getString(R.string.app_ok), R.drawable.bg_btn_blue, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_AUTOCOMPLETE && resultCode == RESULT_OK && data != null) {
            Place place = Autocomplete.getPlaceFromIntent(data);
            address = place.getAddress();
            this.<TextView>findViewById(R.id.textView_address).setText("\n" + address);
            if (map != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), DEFAULT_ZOOM));
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        App.get(this).getFusedLocationClient().getLastLocation().addOnSuccessListener(result -> {
            if (result != null) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(result.getLatitude(), result.getLongitude()), DEFAULT_ZOOM));
            }
        });
        map.setOnCameraIdleListener(() -> {
            LatLng center = map.getCameraPosition().target;
            address = null;
            findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... voids) {
                    try {
                        List<Address> addresses = new Geocoder(AddressActivity.this)
                                .getFromLocation(center.latitude, center.longitude, 1);
                        if (addresses.size() > 0) {
                            Address address = addresses.get(0);
                            StringBuilder strAddress = new StringBuilder();
                            for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                                strAddress.append(address.getAddressLine(i));
                                if (i < address.getMaxAddressLineIndex()) {
                                    strAddress.append(", ");
                                }
                            }
                            return strAddress.toString();
                        }
                    } catch (Exception e) { }
                    return Math.round(center.latitude * 100_000) / 100_000.0 + ", " + Math.round(center.longitude * 100_000) / 100_000.0;
                }
                @Override
                protected void onPostExecute(String address) {
                    if (center.equals(map.getCameraPosition().target)) {
                        findViewById(R.id.progressBar).setVisibility(View.GONE);
                        AddressActivity.this.address = address;
                        AddressActivity.this.<TextView>findViewById(R.id.textView_address).setText("\n" + address);
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        });
    }

    public void onButtonSearch(View v) {
        startActivityForResult(new Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, Arrays.asList(Place.Field.ADDRESS, Place.Field.LAT_LNG))
                .setCountry(App.get(this).prefs().getString(Prefs.COUNTRY_CODE, null))
                .setTypeFilter(TypeFilter.ADDRESS)
                .setHint(getString(R.string.home_address))
                .build(this), REQUEST_AUTOCOMPLETE);
    }

    public void onButtonPick(View v) {
        if (address == null) {
            return;
        }
        new ConfirmDialog(this, getString(R.string.address_confirm, address))
                .setButton1(getString(R.string.address_confirm_confirm), R.drawable.bg_btn_green, view -> {
                    LatLng center = map.getCameraPosition().target;
                    setResult(RESULT_OK, new Intent()
                            .putExtras(getIntent())
                            .putExtra(EXTRA_ADDRESS, address)
                            .putExtra(EXTRA_LAT, center.latitude)
                            .putExtra(EXTRA_LNG, center.longitude));
                    finish();
                })
                .setButton2(getString(R.string.address_confirm_change), R.drawable.bg_btn_red, null)
                .show();
    }
}
