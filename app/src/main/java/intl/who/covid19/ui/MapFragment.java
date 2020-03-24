package intl.who.covid19.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.google.maps.android.ui.IconGenerator;

import java.util.ArrayList;
import java.util.List;

import intl.who.covid19.App;
import intl.who.covid19.CountryDefaults;
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

    private static final int DEFAULT_ZOOM = 12;
    private static final int MARKER_ZOOM_LEVEL = 10;

    private Button button_toggle;
    private RecyclerView recyclerView_counties;
    private GoogleMap map;
    private List<CountyStats> counties;
    private IconGenerator iconGenerator;
    private boolean markersVisible = true;
    private final ArrayList<Marker> markers = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        button_toggle = view.findViewById(R.id.button_toggle);
        button_toggle.setOnClickListener(v -> toggleListMap());
        SupportMapFragment fragment_map = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.fragment_map);
        if (fragment_map != null) {
            fragment_map.getMapAsync(this);
        }
        recyclerView_counties = view.findViewById(R.id.recyclerView_counties);
        // Initialize the recycler view
        recyclerView_counties.setLayoutManager(new LinearLayoutManager(view.getContext()));
        recyclerView_counties.setAdapter(new RecyclerView.Adapter() {
            @Override
            public int getItemCount() {
                return counties.size();
            }
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.view_county_stat, parent, false)) { };
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                CountyStats county = counties.get(position);
                ((TextView) holder.itemView.findViewById(R.id.textView_name)).setText(county.name);
                ((TextView) holder.itemView.findViewById(R.id.textView_region)).setText(county.region);
                ((TextView) holder.itemView.findViewById(R.id.textView_number)).setText(String.valueOf(county.positive));
                holder.itemView.setOnClickListener(v -> {
                    toggleListMap();
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
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(CountryDefaults.CENTER_LAT, CountryDefaults.CENTER_LNG), CountryDefaults.CENTER_ZOOM));
        App.get(getContext()).getFusedLocationClient().getLastLocation().addOnSuccessListener(result -> {
            if (result != null) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(result.getLatitude(), result.getLongitude()), DEFAULT_ZOOM));
            }
        });
        map.setOnCameraMoveListener(() -> {
            boolean markersAreToBeVisible = map.getCameraPosition().zoom >= MARKER_ZOOM_LEVEL;
            if ((markersAreToBeVisible && !markersVisible) || (!markersAreToBeVisible && markersVisible)) {
                markersVisible = markersAreToBeVisible;
                for (Marker m : markers) {
                    m.setVisible(markersVisible);
                }
            }
        });
        iconGenerator = new IconGenerator(getContext());
        iconGenerator.setBackground(null);
        iconGenerator.setTextAppearance(getContext(), R.style.Poppins_TextViewStyle);
        updateMapCircles();
    }

    private boolean isInMap() {
        return recyclerView_counties.getVisibility() == View.GONE;
    }

    private void toggleListMap() {
        if (isInMap()) {
            button_toggle.setText(R.string.home_map_map);
            recyclerView_counties.setVisibility(View.VISIBLE);
        } else {
            button_toggle.setText(R.string.home_map_list);
            recyclerView_counties.setVisibility(View.GONE);
        }
    }

    private void reloadData() {
        CountryDefaults.getCountyStats(getContext(), countyStats -> {
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
        markers.clear();
        for (CountyStats county : counties) {
            if (county.positive > 0) {
                LatLng latLng = new LatLng(county.lat, county.lng);
                map.addCircle(new CircleOptions()
                        .center(latLng)
                        .radius(1000 + 5000.0 * county.positive / 100)
                        .strokeWidth(3)
                        .strokeColor(ResourcesCompat.getColor(getResources(), R.color.red, null))
                        .fillColor(ResourcesCompat.getColor(getResources(), R.color.red, null) & 0x66ffffff));
                markers.add(map.addMarker(new MarkerOptions()
                        .position(latLng)
                        .anchor(.5f, .75f)
                        .icon(BitmapDescriptorFactory.fromBitmap(iconGenerator.makeIcon(county.name)))
                        .visible(markersVisible)));
                markers.add(map.addMarker(new MarkerOptions()
                        .position(latLng)
                        .anchor(.5f, .25f)
                        .icon(BitmapDescriptorFactory.fromBitmap(iconGenerator.makeIcon(String.valueOf(county.positive))))
                        .visible(markersVisible)));
            }
        }
    }
}
