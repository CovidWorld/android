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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.tabs.TabLayout;
import com.google.maps.android.ui.IconGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import intl.who.covid19.App;
import intl.who.covid19.ICountryDefaults;
import intl.who.covid19.R;

public class MapFragment extends Fragment implements OnMapReadyCallback {
    public static class CountyStats {
        public long id;
        public String name;
        public String region;
        public double lat;
        public double lng;
        public int positive;
    }

    private static final int DEFAULT_ZOOM = 11;
    private static final float COUNTY_STATS_ZOOM_LEVEL = 8f;

    private TextView textView_statsTotal;
    private TextView textView_statsRecovered;
    private SupportMapFragment fragment_map;
    private RecyclerView recyclerView_counties;
    private GoogleMap map;
    private List<CountyStats> counties;
    private IconGenerator iconGenerator;
    private TextView textView_bubble;
    private boolean showCounties = true;
    private final ArrayList<Marker> markersStats = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TabLayout tabLayout = view.findViewById(R.id.tabLayout_map);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if ((isInMap() && tab.getPosition() == 1) || (!isInMap() && tab.getPosition() == 0)) {
                    toggleListMap();
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }
            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });
        textView_statsTotal = view.findViewById(R.id.textView_statsTotal);
        textView_statsRecovered = view.findViewById(R.id.textView_statsRecovered);
        fragment_map = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.fragment_map);
        if (fragment_map != null) {
            fragment_map.getMapAsync(this);
        }
        recyclerView_counties = view.findViewById(R.id.recyclerView_counties);
        // Initialize the recycler view
        recyclerView_counties.setLayoutManager(new LinearLayoutManager(view.getContext()));
        recyclerView_counties.setAdapter(new RecyclerView.Adapter() {
            @Override
            public int getItemCount() {
                return (counties != null ? counties.size() : 0);
            }
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.view_county_stat, parent, false)) {
                };
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                CountyStats county = counties.get(position);
                ((TextView) holder.itemView.findViewById(R.id.textView_name)).setText(county.name);
                ((TextView) holder.itemView.findViewById(R.id.textView_region)).setText(county.region);
                ((TextView) holder.itemView.findViewById(R.id.textView_number)).setText(String.valueOf(county.positive));
                holder.itemView.setOnClickListener(v -> {
                    //toggleListMap();
                    tabLayout.selectTab(tabLayout.getTabAt(0));
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(county.lat, county.lng), DEFAULT_ZOOM));
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadData();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        ICountryDefaults cd = App.get(getContext()).getCountryDefaults();
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(cd.getCenterLat(), cd.getCenterLng()), (float) cd.getCenterZoom()));
        App.get(getContext()).getFusedLocationClient().getLastLocation().addOnSuccessListener(result -> {
            if (result != null) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(result.getLatitude(), result.getLongitude()), DEFAULT_ZOOM));
            }
        });
        map.setOnCameraMoveListener(() -> {
            boolean showCounties = map.getCameraPosition().zoom >= COUNTY_STATS_ZOOM_LEVEL;
            if (showCounties != this.showCounties) {
                this.showCounties = showCounties;
                updateMapCircles();
            }
        });
        textView_bubble = (TextView) getLayoutInflater().inflate(R.layout.view_map_bubble, null);
        iconGenerator = new IconGenerator(getContext());
        iconGenerator.setBackground(null);
        iconGenerator.setContentView(textView_bubble);
        updateMapCircles();
    }

    private boolean isInMap() {
        return recyclerView_counties.getVisibility() == View.GONE;
    }

    private void toggleListMap() {
        if (isInMap()) {
            getChildFragmentManager().beginTransaction().hide(fragment_map).commit();
            recyclerView_counties.setVisibility(View.VISIBLE);
        } else {
            getChildFragmentManager().beginTransaction().show(fragment_map).commit();
            recyclerView_counties.setVisibility(View.GONE);
        }
    }

    private void reloadData() {
        App.get(getContext()).getCountryDefaults().getStats(getContext(), stats -> {
            if (getActivity() == null || getActivity().isFinishing()) {
                return;
            }
            textView_statsTotal.setText(stats == null ? "..." : String.valueOf(stats.positive));
            textView_statsRecovered.setText(stats == null ? "..." : String.valueOf(stats.recovered));
        });
        App.get(getContext()).getCountryDefaults().getCountyStats(getContext(), countyStats -> {
            if (getActivity() == null || getActivity().isFinishing()) {
                return;
            }
            this.counties = countyStats;
            recyclerView_counties.getAdapter().notifyDataSetChanged();
            updateMapCircles();
        });
    }

    private void updateMapCircles() {
        if (map == null || counties == null) {
            return;
        }
        map.clear();
        markersStats.clear();
        List<CountyStats> counties = showCounties ? this.counties : reduceToRegions();
        for (CountyStats county : counties) {
            if (county.positive > 0) {
                LatLng latLng = new LatLng(county.lat, county.lng);
                map.addCircle(new CircleOptions()
                        .center(latLng)
                        .radius(1000 + 5000.0 * county.positive / 100)
                        .strokeWidth(3)
                        .strokeColor(ResourcesCompat.getColor(getResources(), R.color.red, null))
                        .fillColor(ResourcesCompat.getColor(getResources(), R.color.red, null) & 0x44ffffff));
                textView_bubble.setText(String.valueOf(county.positive));
                markersStats.add(map.addMarker(new MarkerOptions()
                        .position(latLng)
                        .anchor(.5f, .875f)
                        .icon(BitmapDescriptorFactory.fromBitmap(iconGenerator.makeIcon()))
                        .title(county.name)));
            }
        }
    }

    private ArrayList<CountyStats> reduceToRegions() {
        HashSet<String> processedRegions = new HashSet<>();
        ArrayList<CountyStats> regions = new ArrayList<>();
        for (int i = 0; i < counties.size(); i++) {
            CountyStats countyStats = counties.get(i);
            if (processedRegions.contains(countyStats.region)) {
                continue;
            }
            CountyStats regionStats = new CountyStats();
            regionStats.name = countyStats.region;
            regionStats.lat = countyStats.lat;
            regionStats.lng = countyStats.lng;
            regionStats.positive = countyStats.positive;
            int count = 1;
            for (int j = i + 1; j < counties.size(); j++) {
                CountyStats stats = counties.get(j);
                if (stats.region.equals(countyStats.region)) {
                    regionStats.lat += stats.lat;
                    regionStats.lng += stats.lng;
                    regionStats.positive += stats.positive;
                    count++;
                }
            }
            regionStats.lat /= count;
            regionStats.lng /= count;
            regions.add(regionStats);
            processedRegions.add(countyStats.region);
        }
        return regions;
    }
}
