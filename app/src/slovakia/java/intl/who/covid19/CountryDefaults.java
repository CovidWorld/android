package intl.who.covid19;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

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
import java.util.List;
import java.util.Map;

import intl.who.covid19.ui.HomeFragment;
import intl.who.covid19.ui.MapFragment;
import sk.turn.http.Http;

public class CountryDefaults {
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
        public int totalRecovered;
        /** Internal field to keep the app from updating too often */
        public long lastUpdate;

        public String toJson() {
            return new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create().toJson(this);
        }
        public HomeFragment.Stats toHomeStats() {
            HomeFragment.Stats stats = new HomeFragment.Stats();
            stats.positive = totalCases;
            stats.recovered = totalRecovered;
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

    public static final double CENTER_LAT = 48.82;
    public static final double CENTER_LNG = 19.62;
    public static final int CENTER_ZOOM = 8;
    private static List<County> counties;

    public static void getStats(Context context, App.Callback<HomeFragment.Stats> callback) {
        Stats stats = Stats.fromJson(App.get(context).prefs().getString("stats", "null"));
        callback.onCallback(stats == null ? null : stats.toHomeStats());
        // Update stats if necessary
        if (stats == null || System.currentTimeMillis() - stats.lastUpdate > 3_600_000L) {
            String statsUrl = App.get(context).getRemoteConfig().getString(App.RC_STATS_URL);
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

    public static void getCountyStats(Context context, App.Callback<List<MapFragment.CountyStats>> callback) {
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
            String statsUrl = App.get(context).getRemoteConfig().getString(App.RC_MAPSTATS_URL);
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

    private static void onCountyStats(CountyStats stats, App.Callback<List<MapFragment.CountyStats>> callback) {
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
}
