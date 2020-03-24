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
