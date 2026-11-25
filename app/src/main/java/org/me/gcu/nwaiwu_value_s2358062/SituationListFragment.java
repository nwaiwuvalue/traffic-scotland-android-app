package org.me.gcu.nwaiwu_value_s2358062;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

/**
 * SituationListFragment presents a list of traffic situations.
 * Supports category/type filtering, road search input, distance radius calculations, and feed pills.
 */
public class SituationListFragment extends Fragment {

    private static final String ARG_SITUATIONS = "arg_situations";
    private static final String ARG_SHOW_SEARCH = "arg_show_search";

    // Fixed reference coordinates (Glasgow city centre) for distance search radius
    private static final double REF_LAT = 55.8642;
    private static final double REF_LNG = -4.2518;

    /**
     * Interface definition for a callback to be invoked when a situation is selected.
     */
    public interface OnSituationSelectedListener {
        void onSituationSelected(Situation situation);
    }

    private OnSituationSelectedListener listener;
    private ArrayList<Situation> masterList = new ArrayList<>();
    private ArrayList<Situation> lastFilteredList = new ArrayList<>();
    private SituationAdapter adapter;
    private boolean showSearch = false;

    // Active filter state for Feed tab pills: "ALL", "Unplanned Event", or "Current Roadworks"
    private String feedFilter = "ALL";

    private EditText searchRoadInput;
    private Spinner categorySpinner;
    private Spinner distanceSpinner;
    private TextView emptyView;
    private MaterialButton btnViewFilteredOnMap;

    private TextView btnFilterAll;
    private TextView btnFilterUnplanned;
    private TextView btnFilterRoadworks;

    public static SituationListFragment newInstance(ArrayList<Situation> situations, boolean showSearch) {
        SituationListFragment fragment = new SituationListFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_SITUATIONS, situations);
        args.putBoolean(ARG_SHOW_SEARCH, showSearch);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnSituationSelectedListener) {
            listener = (OnSituationSelectedListener) context;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            ArrayList<Situation> list = (ArrayList<Situation>) getArguments().getSerializable(ARG_SITUATIONS);
            if (list != null) masterList = list;
            showSearch = getArguments().getBoolean(ARG_SHOW_SEARCH, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_situation_list, container, false);

        View searchControls = view.findViewById(R.id.searchControlsContainer);
        View feedFilterBar = view.findViewById(R.id.feedFilterBar);

        if (searchControls != null) {
            searchControls.setVisibility(showSearch ? View.VISIBLE : View.GONE);
        }
        if (feedFilterBar != null) {
            feedFilterBar.setVisibility(showSearch ? View.GONE : View.VISIBLE);
        }

        searchRoadInput = view.findViewById(R.id.searchRoadInput);
        categorySpinner = view.findViewById(R.id.categorySpinner);
        distanceSpinner = view.findViewById(R.id.distanceSpinner);
        emptyView = view.findViewById(R.id.emptyView);

        btnViewFilteredOnMap = view.findViewById(R.id.btnViewFilteredOnMap);
        if (btnViewFilteredOnMap != null) {
            btnViewFilteredOnMap.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showFilteredOnMap(new ArrayList<>(lastFilteredList));
                }
            });
        }

        btnFilterAll = view.findViewById(R.id.btnFilterAll);
        btnFilterUnplanned = view.findViewById(R.id.btnFilterUnplanned);
        btnFilterRoadworks = view.findViewById(R.id.btnFilterRoadworks);

        RecyclerView recyclerView = view.findViewById(R.id.situationsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SituationAdapter(new ArrayList<>(masterList), situation -> {
            if (listener != null) listener.onSituationSelected(situation);
        });
        recyclerView.setAdapter(adapter);

        setupFeedFilterButtons();
        setupFilters();
        applyFilters();

        return view;
    }

    /**
     * Configures floating feed filter pill buttons.
     */
    private void setupFeedFilterButtons() {
        btnFilterAll.setOnClickListener(v -> {
            feedFilter = "ALL";
            updatePillStyles();
            applyFilters();
        });

        btnFilterUnplanned.setOnClickListener(v -> {
            feedFilter = "Unplanned Event";
            updatePillStyles();
            applyFilters();
        });

        btnFilterRoadworks.setOnClickListener(v -> {
            feedFilter = "Current Roadworks";
            updatePillStyles();
            applyFilters();
        });

        updatePillStyles();
    }

    /**
     * Updates active visual selection styling of feed filter pills.
     */
    private void updatePillStyles() {
        stylePill(btnFilterAll, "ALL".equals(feedFilter), "#1565C0");
        stylePill(btnFilterUnplanned, "Unplanned Event".equals(feedFilter), "#C62828");
        stylePill(btnFilterRoadworks, "Current Roadworks".equals(feedFilter), "#EF6C00");
    }

    private void stylePill(TextView pill, boolean isSelected, String activeHexColor) {
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
     * Sets up category/distance spinners and text change listener for road name search.
     */
    private void setupFilters() {
        String[] categories = {"All Types", "Unplanned Event", "Current Roadworks"};
        categorySpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, categories));

        String[] distances = {"Any Distance", "Within 10 km", "Within 25 km", "Within 50 km", "Within 100 km"};
        distanceSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, distances));

        AdapterView.OnItemSelectedListener spinnerListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        categorySpinner.setOnItemSelectedListener(spinnerListener);
        distanceSpinner.setOnItemSelectedListener(spinnerListener);

        searchRoadInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilters(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * Updates data in master list and re-applies current active filters.
     */
    public void updateData(ArrayList<Situation> newSituations) {
        this.masterList = newSituations != null ? newSituations : new ArrayList<>();
        if (adapter != null) {
            applyFilters();
        }
    }

    /**
     * Filters situation items based on search query string, selected category, and max distance radius.
     */
    private void applyFilters() {
        if (!showSearch) {
            ArrayList<Situation> feedFiltered = new ArrayList<>();
            for (Situation s : masterList) {
                if ("ALL".equals(feedFilter) || feedFilter.equalsIgnoreCase(s.getFeedCategory())) {
                    feedFiltered.add(s);
                }
            }
            adapter.updateList(feedFiltered);
            emptyView.setVisibility(feedFiltered.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }

        String query = searchRoadInput.getText() != null
                ? searchRoadInput.getText().toString().trim().toLowerCase()
                : "";
        String selectedCategory = categorySpinner.getSelectedItem().toString();
        int distIndex = distanceSpinner.getSelectedItemPosition();

        float maxKm = Float.MAX_VALUE;
        if (distIndex == 1) maxKm = 10f;
        else if (distIndex == 2) maxKm = 25f;
        else if (distIndex == 3) maxKm = 50f;
        else if (distIndex == 4) maxKm = 100f;

        ArrayList<Situation> filtered = new ArrayList<>();
        for (Situation s : masterList) {
            if (!"All Types".equals(selectedCategory)
                    && !selectedCategory.equalsIgnoreCase(s.getFeedCategory())) {
                continue;
            }
            if (!query.isEmpty()) {
                boolean matchesRoad = s.getRoadName().toLowerCase().contains(query);
                boolean matchesComment = s.getComment().toLowerCase().contains(query);
                if (!matchesRoad && !matchesComment) continue;
            }
            if (maxKm < Float.MAX_VALUE && s.distanceKmFrom(REF_LAT, REF_LNG) > maxKm) {
                continue;
            }
            filtered.add(s);
        }

        lastFilteredList = filtered;
        adapter.updateList(filtered);
        if (emptyView != null) {
            emptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }

        if (btnViewFilteredOnMap != null) {
            btnViewFilteredOnMap.setEnabled(!filtered.isEmpty());
            btnViewFilteredOnMap.setText("View " + filtered.size() + " Result" + (filtered.size() == 1 ? "" : "s") + " on Map");
        }
    }
}