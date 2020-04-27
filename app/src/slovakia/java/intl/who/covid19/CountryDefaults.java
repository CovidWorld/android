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
import android.os.Handler;
import android.os.Looper;

import com.auth0.android.jwt.JWT;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import intl.who.covid19.ui.HomeFragment;
import intl.who.covid19.ui.MapFragment;
import intl.who.covid19.ui.PhoneVerificationActivity;
import sk.turn.http.Http;

public class CountryDefaults implements ICountryDefaults {
    public static class Stats {
        public static Stats fromJson(String json) {
            return new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create().fromJson(json, Stats.class);
        }

        public int activeCases;
        public int newCases;
        public int newDeaths;
        public int seriousCritical;
        public int topCases;
        public int totalCases;
        public int totalDeaths;
        public int recovered;
        /** Internal field to keep the app from updating too often */
        public long lastUpdate;

        public String toJson() {
            return new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create().toJson(this);
        }
        public HomeFragment.Stats toHomeStats() {
            HomeFragment.Stats stats = new HomeFragment.Stats();
            stats.positive = totalCases;
            stats.recovered = recovered;
            stats.deaths = totalDeaths;
            return stats;
        }
    }
    private static class Location {
        public double lat;
        public double lon;
    }
    private static class County {
        @SerializedName("IDN3")
        public int id;
        @SerializedName("NM3")
        public String name;
        @SerializedName("IDN2")
        public int regionId;
        @SerializedName("NM2")
        public String regionName;
        public Location location;
    }
    public static class CountyStats {
        public static CountyStats fromJson(String json) {
            return new Gson().fromJson(json, CountyStats.class);
        }
        public List<Map<String, CountyStat>> features;
        /** Internal field to keep the app from updating too often */
        public long lastUpdate;
        public String toJson() {
            return new Gson().toJson(this);
        }
    }
    public static class CountyStat {
        @SerializedName("IDN3")
        public int id;
        @SerializedName("POTVRDENI")
        public int confirmed;
        @SerializedName("VYLIECENI")
        public int recovered;
        @SerializedName("MRTVI")
        public int deaths;
    }
    public static class ValidateOtpResp {
        HashMap<String, String> payload;
        ArrayList<HashMap<String, String>> errors;
    }

    private Context context;
    private List<County> counties;

    public CountryDefaults(Context context) {
        this.context = context;
    }

    @Override
    public boolean verifyPhoneNumberAtStart() { return false; }
    @Override
    public boolean showQuarantineStartPicker() { return false; }
    @Override
    public boolean useFaceId() { return false; }
    @Override
    public boolean sendLocationInQuarantine() {
        return App.get(context).getRemoteConfig().getLong("reportQuarantineLocation") != 0;
    }
    @Override
    public boolean sendQuarantineLeft() {
        return App.get(context).getRemoteConfig().getLong("reportQuarantineExit") != 0;
    }
    @Override
    public boolean roundEncounterTimestampToDays() { return true; }
    @Override
    public String getCountryCode() { return "SK"; }
    @Override
    public double getCenterLat() { return 48.82; }
    @Override
    public double getCenterLng() { return 19.62; }
    @Override
    public double getCenterZoom() { return 8; }

    @Override
    public void getStats(Context context, App.Callback<HomeFragment.Stats> callback) {
        Stats stats = Stats.fromJson(App.get(context).prefs().getString("stats", "null"));
        callback.onCallback(stats == null ? null : stats.toHomeStats());
        // Update stats if necessary
        if (stats == null || System.currentTimeMillis() - stats.lastUpdate > 3_600_000L) {
            String statsUrl = App.get(context).getRemoteConfig().getString("statsUrl");
            if (statsUrl.isEmpty()) {
                return;
            }
            new Http(statsUrl, Http.GET).send(http -> {
                if (http.getResponseCode() != 200) {
                    return;
                }
                Stats newStats = Stats.fromJson(http.getResponseString());
                newStats.lastUpdate = System.currentTimeMillis();
                new Handler(Looper.getMainLooper()).post(() -> {
                    App.get(context).prefs().edit().putString("stats", newStats.toJson()).apply();
                    callback.onCallback(newStats.toHomeStats());
                });
            });
        }
    }

    @Override
    public void getCountyStats(Context context, App.Callback<List<MapFragment.CountyStats>> callback) {
        if (counties == null) {
            try (InputStream inputStream = context.getResources().openRawResource(R.raw.counties)) {
                counties = new Gson().fromJson(new InputStreamReader(inputStream), new TypeToken<List<County>>() { }.getType());
                Collections.sort(counties, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
            } catch (IOException e) {
                counties = new ArrayList<>();
            }
        }
        CountyStats stats = CountyStats.fromJson(App.get(context).prefs().getString("statsCounties", "null"));
        if (stats != null) {
            onCountyStats(stats, callback);
        }
        // Update stats if necessary
        if (stats == null || System.currentTimeMillis() - stats.lastUpdate > 3_600_000L) {
            String statsUrl = App.get(context).getRemoteConfig().getString("mapStatsUrl");
            if (statsUrl.isEmpty()) {
                return;
            }
            new Http(statsUrl, Http.GET).send(http -> {
                if (http.getResponseCode() != 200) {
                    return;
                }
                CountyStats newStats = CountyStats.fromJson(http.getResponseString());
                newStats.lastUpdate = System.currentTimeMillis();
                new Handler(Looper.getMainLooper()).post(() -> {
                    App.get(context).prefs().edit().putString("statsCounties", newStats.toJson()).apply();
                    onCountyStats(newStats, callback);
                });
            });
        }
    }

    private void onCountyStats(CountyStats stats, App.Callback<List<MapFragment.CountyStats>> callback) {
        ArrayList<MapFragment.CountyStats> css = new ArrayList<>();
        for (County county : counties) {
            MapFragment.CountyStats cs = new MapFragment.CountyStats();
            cs.id = county.id;
            cs.name = county.name;
            cs.region = county.regionName;
            cs.lat = county.location.lat;
            cs.lng = county.location.lon;
            for (Map<String, CountyStat> csm : stats.features) {
                CountyStat countyStat = csm.get("attributes");
                if (countyStat != null && countyStat.id == county.id) {
                    cs.positive = countyStat.confirmed;
                    break;
                }
            }
            css.add(cs);
        }
        callback.onCallback(css);
    }

    @Override
    public void sendVerificationCodeText(Context context, String phoneNumber, App.Callback<Exception> callback) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("vPhoneNumber", phoneNumber);
        Handler handler = new Handler();
        new Http(getPhoneVerificationUrlBase(context) + "send-otp", Http.POST)
                .setData(new Gson().toJson(data))
                .send(http -> {
                    ValidateOtpResp resp = http.getResponseCode() / 100 == 2 ? new Gson().fromJson(http.getResponseString(), ValidateOtpResp.class) : null;
                    handler.post(() -> callback.onCallback(http.getResponseCode() == 200 ? null :
                            resp != null && resp.errors.size() >= 1 ? new Exception(resp.errors.get(0).get("description")) :
                                    new IOException(http.getResponseCode() + " " + http.getResponseMessage())));
                });
    }

    @Override
    public int getVerificationCodeLength() { return 6; }

    @Override
    public void checkVerificationCode(Context context, String phoneNumber, String code, App.Callback<PhoneVerificationActivity.QuarantineDetails> callback) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("vPhoneNumber", phoneNumber);
        data.put("nOtp", Long.parseLong(code));
        Handler handler = new Handler();
        new Http(getPhoneVerificationUrlBase(context) + "validate-otp", Http.POST)
                .setData(new Gson().toJson(data))
                .send(http -> {
                    ValidateOtpResp resp = http.getResponseCode() == 200 ? new Gson().fromJson(http.getResponseString(), ValidateOtpResp.class) : null;
                    handler.post(() -> {
                        PhoneVerificationActivity.QuarantineDetails qd = null;
                        if (resp != null && resp.payload != null) {
                            try {
                                JWT jwt = new JWT(resp.payload.get("vAccessToken"));
                                qd = new PhoneVerificationActivity.QuarantineDetails();
                                qd.covidId = jwt.getSubject();
                                qd.quarantineStart = jwt.getClaim("qs").asString();
                                qd.quarantineEnd = jwt.getClaim("qe").asString();
                            } catch (Exception e) {
                                App.log("CountryDefaults.checkVerificationCode " + e);
                            }
                        }
                        callback.onCallback(qd);
                    });
                });
    }

    private String getPhoneVerificationUrlBase(Context context) {
        String urlBase = BuildConfig.PHONE_VERIFICATION_API_URL;
        String remoteConfigHost = App.get(context).getRemoteConfig().getString("ncziApiHost");
        if (!remoteConfigHost.isEmpty()) {
            urlBase = remoteConfigHost + "/api/v1/sygic/";
        }
        return urlBase;
    }
}
