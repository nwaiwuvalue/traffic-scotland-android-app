package org.me.gcu.nwaiwu_value_s2358062;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.Locale;

/**
 * TrafficMapFragment displays interactive Google Map markers and polylines for traffic situations.
 * Features category filter pills, custom markers, info windows, and proximity-based "Near Me" filtering.
 */
public class TrafficMapFragment extends Fragment implements OnMapReadyCallback {

    private static final String ARG_SITUATIONS = "arg_situations";
    private static final LatLng SCOTLAND_CENTER = new LatLng(56.15, -4.0);
    private static final float DEFAULT_ZOOM = 7.0f;
    private static final float NEAR_ME_RADIUS_METERS = 5000f; // 5km radius

    private GoogleMap googleMap;
    private ArrayList<Situation> situations = new ArrayList<>();
    private SituationListFragment.OnSituationSelectedListener listener;

    private String activeFilter = "ALL";
    private TextView btnMapFilterAll;
    private TextView btnMapFilterUnplanned;
    private TextView btnMapFilterRoadworks;
    private TextView btnNearMeToggle;

    private boolean nearMeOnly = false;
    private Location userLocation = null;

    private BitmapDescriptor unplannedIcon;
    private BitmapDescriptor roadworksIcon;

    // Runtime location permission launcher for Near Me mode
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    enableNearMeMode();
                } else {
                    Toast.makeText(requireContext(),
                            "Location permission is needed to show nearby incidents.",
                            Toast.LENGTH_SHORT).show();
                }
            });

    public static TrafficMapFragment newInstance(ArrayList<Situation> situations) {
        TrafficMapFragment fragment = new TrafficMapFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_SITUATIONS, situations);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof SituationListFragment.OnSituationSelectedListener) {
            listener = (SituationListFragment.OnSituationSelectedListener) context;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            ArrayList<Situation> list = (ArrayList<Situation>) getArguments().getSerializable(ARG_SITUATIONS);
            if (list != null) {
                situations = list;
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_traffic_map, container, false);

        btnMapFilterAll = view.findViewById(R.id.btnMapFilterAll);
        btnMapFilterUnplanned = view.findViewById(R.id.btnMapFilterUnplanned);
        btnMapFilterRoadworks = view.findViewById(R.id.btnMapFilterRoadworks);
        btnNearMeToggle = view.findViewById(R.id.btnNearMeToggle);

        setupMapFilterButtons();
        setupNearMeButton();

        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        return view;
    }

    /**
     * Called when Google Map is ready for initialization.
     */
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(SCOTLAND_CENTER, DEFAULT_ZOOM));

        // Create and cache custom circular bitmap markers
        unplannedIcon = createCustomMarkerIcon(
                Color.parseColor("#C62828"),
                android.R.drawable.ic_dialog_alert
        );
        roadworksIcon = createCustomMarkerIcon(
                Color.parseColor("#EF6C00"),
                android.R.drawable.ic_menu_manage
        );

        // Marker info window click listener to show situation details
        googleMap.setOnInfoWindowClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof Situation && listener != null) {
                listener.onSituationSelected((Situation) tag);
            }
        });

        plotMarkers();
    }

    public void updateMarkers(ArrayList<Situation> newSituations) {
        this.situations = newSituations != null ? newSituations : new ArrayList<>();
        if (googleMap != null) {
            plotMarkers();
        }
    }

    private void setupMapFilterButtons() {
        btnMapFilterAll.setOnClickListener(v -> {
            activeFilter = "ALL";
            updatePillStyles();
            plotMarkers();
        });

        btnMapFilterUnplanned.setOnClickListener(v -> {
            activeFilter = "Unplanned Event";
            updatePillStyles();
            plotMarkers();
        });

        btnMapFilterRoadworks.setOnClickListener(v -> {
            activeFilter = "Current Roadworks";
            updatePillStyles();
            plotMarkers();
        });

        updatePillStyles();
    }

    private void setupNearMeButton() {
        if (btnNearMeToggle == null) return;

        btnNearMeToggle.setOnClickListener(v -> {
            if (nearMeOnly) {
                nearMeOnly = false;
                updateNearMeStyle();
                if (googleMap != null) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(SCOTLAND_CENTER, DEFAULT_ZOOM));
                }
                plotMarkers();
            } else {
                requestLocationOrEnable();
            }
        });

        updateNearMeStyle();
    }

    private void requestLocationOrEnable() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableNearMeMode();
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void enableNearMeMode() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        if (lm != null) {
            Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            best = (gps != null) ? gps : net;
        }

        if (best == null) {
            Toast.makeText(requireContext(),
                    "Couldn't get your current location yet. Check GPS is enabled.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        userLocation = best;
        nearMeOnly = true;
        updateNearMeStyle();

        if (googleMap != null) {
            googleMap.setMyLocationEnabled(true);
            LatLng myPos = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myPos, 12.5f));
        }
        plotMarkers();
    }

    private void updateNearMeStyle() {
        if (btnNearMeToggle == null) return;

        if (nearMeOnly) {
            btnNearMeToggle.setText("Near Me - On");
            btnNearMeToggle.setBackgroundColor(Color.parseColor("#1565C0"));
            btnNearMeToggle.setTextColor(Color.WHITE);
        } else {
            btnNearMeToggle.setText("Near Me - Off");
            btnNearMeToggle.setBackgroundColor(Color.TRANSPARENT);
            btnNearMeToggle.setTextColor(Color.parseColor("#546E7A"));
        }
    }

    private void updatePillStyles() {
        stylePill(btnMapFilterAll, "ALL".equals(activeFilter), "#1565C0");
        stylePill(btnMapFilterUnplanned, "Unplanned Event".equals(activeFilter), "#C62828");
        stylePill(btnMapFilterRoadworks, "Current Roadworks".equals(activeFilter), "#EF6C00");
    }

    private void stylePill(TextView pill, boolean isSelected, String activeHexColor) {
        if (pill == null) return;

        if (isSelected) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor(activeHexColor));
            bg.setCornerRadius(60f);
            pill.setBackground(bg);
            pill.setTextColor(Color.WHITE);
        } else {
            pill.setBackground(null);
            pill.setTextColor(Color.parseColor("#546E7A"));
        }
    }

    /**
     * Clears map and plots markers and polylines for active situations.
     */
    private void plotMarkers() {
        if (googleMap == null) return;
        googleMap.clear();

        for (Situation s : situations) {
            if (!s.hasValidLocation()) continue;

            if (!"ALL".equals(activeFilter) && !activeFilter.equalsIgnoreCase(s.getFeedCategory())) {
                continue;
            }

            if (nearMeOnly && userLocation != null) {
                float[] results = new float[1];
                Location.distanceBetween(
                        userLocation.getLatitude(), userLocation.getLongitude(),
                        s.getFromLat(), s.getFromLng(),
                        results
                );
                if (results[0] > NEAR_ME_RADIUS_METERS) {
                    continue;
                }
            }

            boolean isRoadwork = s.getFeedCategory() != null
                    && s.getFeedCategory().toLowerCase(Locale.UK).contains("roadwork");

            BitmapDescriptor markerIcon = isRoadwork ? roadworksIcon : unplannedIcon;

            LatLng startPos = new LatLng(s.getFromLat(), s.getFromLng());
            String title = (s.getRoadName() != null && !s.getRoadName().isEmpty())
                    ? s.getRoadName() + " • " + s.getSituationType()
                    : s.getSituationType();

            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(startPos)
                    .title(title)
                    .snippet("Tap for full details")
                    .icon(markerIcon));

            if (marker != null) {
                marker.setTag(s);
            }

            // Draw coloured polyline for linear roadwork stretches
            if (s.hasLinearSegment()) {
                LatLng endPos = new LatLng(s.getToLat(), s.getToLng());
                googleMap.addPolyline(new PolylineOptions()
                        .add(startPos, endPos)
                        .width(10f)
                        .color(isRoadwork ? Color.parseColor("#EF6C00") : Color.parseColor("#C62828")));
            }
        }
    }

    /**
     * Generates a circular bitmap descriptor icon containing a Material drawable icon.
     */
    private BitmapDescriptor createCustomMarkerIcon(int bgColor, int drawableResId) {
        float density = getResources().getDisplayMetrics().density;
        int sizePx = Math.round(36 * density);

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, borderPaint);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(bgColor);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx / 2f) - (2.5f * density), fillPaint);

        Drawable icon = ContextCompat.getDrawable(requireContext(), drawableResId);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(Color.WHITE);
            int padding = Math.round(8 * density);
            icon.setBounds(padding, padding, sizePx - padding, sizePx - padding);
            icon.draw(canvas);
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }
}