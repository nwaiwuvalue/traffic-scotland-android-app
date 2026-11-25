package org.me.gcu.nwaiwu_value_s2358062;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SituationDetailFragment displays full metadata for a selected traffic situation.
 * Renders location details, schedule timings, coordinates, and reverse-geocoded place names.
 */
public class SituationDetailFragment extends Fragment {

    private static final String ARG_SITUATION = "arg_situation";
    private Situation situation;

    private final ExecutorService geocoderExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static SituationDetailFragment newInstance(Situation situation) {
        SituationDetailFragment fragment = new SituationDetailFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_SITUATION, situation);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            situation = (Situation) getArguments().getSerializable(ARG_SITUATION);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_situation_detail, container, false);
        if (situation == null) return view;

        TextView badge = view.findViewById(R.id.detailCategoryBadge);
        TextView title = view.findViewById(R.id.detailTitle);
        TextView roadName = view.findViewById(R.id.detailRoadName);
        TextView comment = view.findViewById(R.id.detailComment);
        TextView placeName = view.findViewById(R.id.detailPlaceName);
        TextView direction = view.findViewById(R.id.detailDirection);
        TextView coordinates = view.findViewById(R.id.detailCoordinates);
        TextView startTime = view.findViewById(R.id.detailStartTime);
        TextView endTime = view.findViewById(R.id.detailEndTime);
        TextView idText = view.findViewById(R.id.detailId);

        // 1. Category Badge (Red for Unplanned Events, Orange for Current Roadworks)
        String category = situation.getFeedCategory() != null ? situation.getFeedCategory() : "Traffic Update";
        badge.setText(category.toUpperCase(Locale.UK));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(40f);
        badgeBg.setColor(category.toLowerCase(Locale.UK).contains("roadwork")
                ? Color.parseColor("#EF6C00")
                : Color.parseColor("#C62828"));
        badge.setBackground(badgeBg);

        // 2. Format Title & Road Name
        title.setText(formatReadableText(situation.getSituationType()));
        String road = situation.getRoadName();
        if (road != null && !road.trim().isEmpty()) {
            roadName.setText("Road: " + road.trim());
            roadName.setVisibility(View.VISIBLE);
        } else {
            roadName.setVisibility(View.GONE);
        }

        // 3. Format Public Comment Description
        String desc = situation.getComment();
        comment.setText((desc != null && !desc.trim().isEmpty())
                ? desc.trim().replaceAll("\\s+", " ")
                : "No additional public comment provided by Traffic Scotland.");

        // 4. Direction & Coordinates + Background Reverse Geocoding
        String dir = situation.getDirection();
        if (dir != null && !dir.trim().isEmpty()) {
            direction.setText("Direction: " + formatReadableText(dir));
            direction.setVisibility(View.VISIBLE);
        } else {
            direction.setVisibility(View.GONE);
        }

        double lat = situation.getLatitude();
        double lng = situation.getLongitude();
        if (lat != 0.0 || lng != 0.0) {
            coordinates.setText(String.format(Locale.UK, "Coordinates: %.5f, %.5f", lat, lng));
            resolvePlaceName(lat, lng, placeName);
        } else {
            placeName.setText("Location not specified");
            coordinates.setVisibility(View.GONE);
        }

        // 5. Format Timestamps
        startTime.setText("Starts: " + formatIsoTimestamp(situation.getStartTime()));
        endTime.setText("Ends: " + formatIsoTimestamp(situation.getEndTime()));

        // 6. Reference ID
        idText.setText("Reference ID: " + (situation.getId() != null ? situation.getId() : "N/A"));

        return view;
    }

    /**
     * Converts raw DATEX II camelCase or snake_case strings (e.g. "roadMaintenance") into Title Case.
     */
    private String formatReadableText(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Traffic Situation";
        String spaced = raw.replace("_", " ")
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .trim();
        String[] words = spaced.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                        .append(w.substring(1).toLowerCase(Locale.UK))
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Converts DATEX II ISO-8601 timestamps (e.g. "2026-11-25T14:30:00Z") into human-readable UK format.
     */
    private String formatIsoTimestamp(String rawTime) {
        if (rawTime == null || rawTime.trim().isEmpty()) return "Not specified";

        String cleaned = rawTime.trim();
        cleaned = cleaned.replaceAll("\\.\\d+", "");

        String[] inputPatterns = {
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss"
        };

        SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.UK);
        outputFormat.setTimeZone(TimeZone.getDefault());

        for (String pattern : inputPatterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.UK);
                if (pattern.endsWith("'Z'")) {
                    parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date parsed = parser.parse(cleaned);
                if (parsed != null) {
                    return outputFormat.format(parsed);
                }
            } catch (ParseException ignored) {
            }
        }
        return rawTime;
    }

    /**
     * Reverse-geocodes latitude/longitude coordinates on a background thread to resolve real place names.
     */
    private void resolvePlaceName(double lat, double lng, TextView targetView) {
        if (!Geocoder.isPresent()) {
            targetView.setText("Scottish Trunk Road Network");
            return;
        }

        geocoderExecutor.execute(() -> {
            String resolved = "Location unavailable offline";
            try {
                Geocoder geocoder = new Geocoder(requireContext(), Locale.UK);
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    StringBuilder sb = new StringBuilder();

                    if (addr.getThoroughfare() != null) {
                        sb.append(addr.getThoroughfare());
                    }
                    if (addr.getLocality() != null) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(addr.getLocality());
                    } else if (addr.getSubAdminArea() != null) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(addr.getSubAdminArea());
                    }
                    if (addr.getPostalCode() != null) {
                        if (sb.length() > 0) sb.append(" (").append(addr.getPostalCode()).append(")");
                        else sb.append(addr.getPostalCode());
                    }

                    if (sb.length() > 0) {
                        resolved = sb.toString();
                    } else if (addr.getAddressLine(0) != null) {
                        resolved = addr.getAddressLine(0);
                    }
                }
            } catch (IOException | IllegalStateException ignored) {
            }

            final String finalPlace = resolved;
            mainHandler.post(() -> {
                if (isAdded()) {
                    targetView.setText(finalPlace);
                }
            });
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        geocoderExecutor.shutdown();
    }
}