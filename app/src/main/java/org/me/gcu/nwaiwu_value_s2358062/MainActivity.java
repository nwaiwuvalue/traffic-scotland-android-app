// Name: Value Nwaiwu
// Student ID: S2358062
// Programme: BSc Computing

package org.me.gcu.nwaiwu_value_s2358062;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.navigation.NavigationBarView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * MainActivity serves as the main entry point and single activity host for the application.
 * Manages bottom navigation fragment switching (Feed, Search, Map) and dual-pane landscape layouts.
 */
public class MainActivity extends AppCompatActivity
        implements SituationListFragment.OnSituationSelectedListener {

    private TrafficViewModel viewModel;
    private ProgressBar loadingIndicator;
    private TextView offlineBanner;
    private NavigationBarView bottomNavigation;

    private boolean isTwoPaneLandscape = false;
    private ArrayList<Situation> allSituations = new ArrayList<>();

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            viewModel.refreshData();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Initialize Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Traffic Scotland");
        }

        // 2. Bind UI Views and check for dual-pane landscape container
        loadingIndicator = findViewById(R.id.loadingIndicator);
        offlineBanner = findViewById(R.id.offlineBanner);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        isTwoPaneLandscape = findViewById(R.id.detailContainer) != null;

        // 3. Apply System Bar Window Insets for edge-to-edge support
        applySystemBarInsets(toolbar, bottomNavigation);

        // 4. Initialize ViewModel and setup observations & navigation listeners
        viewModel = new ViewModelProvider(this).get(TrafficViewModel.class);
        setupNavigation();
        observeViewModel();

        // 5. Load default Feed fragment on first launch or restore detail pane on rotation
        if (savedInstanceState == null) {
            loadMainFragment(SituationListFragment.newInstance(allSituations, false));
            viewModel.refreshData();
        } else if (isTwoPaneLandscape) {
            Situation selected = viewModel.getSelectedSituation().getValue();
            if (selected != null) {
                showDetailInLandscape(selected);
            }
        }
    }

    /**
     * Adjusts padding to prevent system bars (status bar & gesture nav) from covering UI elements.
     */
    private void applySystemBarInsets(Toolbar toolbar, NavigationBarView navView) {
        final int initialToolbarTop = toolbar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, windowInsets) -> {
            Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    initialToolbarTop + statusBars.top,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return windowInsets;
        });

        final int initialNavBottom = navView.getPaddingBottom();
        final int initialNavRight = navView.getPaddingRight();
        ViewCompat.setOnApplyWindowInsetsListener(navView, (v, windowInsets) -> {
            Insets navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    initialNavRight + (isTwoPaneLandscape ? navBars.right : 0),
                    initialNavBottom + (isTwoPaneLandscape ? 0 : navBars.bottom)
            );
            return windowInsets;
        });
    }

    /**
     * Configures BottomNavigationView item selection handlers.
     */
    private void setupNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_feed) {
                loadMainFragment(SituationListFragment.newInstance(allSituations, false));
                return true;
            } else if (id == R.id.nav_search) {
                loadMainFragment(SituationListFragment.newInstance(allSituations, true));
                return true;
            } else if (id == R.id.nav_map) {
                loadMainFragment(TrafficMapFragment.newInstance(allSituations));
                return true;
            }
            return false;
        });
    }

    /**
     * Observes LiveData streams from TrafficViewModel to update UI state in real-time.
     */
    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (loadingIndicator != null) {
                loadingIndicator.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getIsOffline().observe(this, isOffline -> {
            if (Boolean.TRUE.equals(isOffline)) {
                updateStatusBanner(false);
            }
        });

        viewModel.getSituations().observe(this, situations -> {
            if (situations == null) return;
            allSituations = situations;

            Boolean offline = viewModel.getIsOffline().getValue();
            updateStatusBanner(!Boolean.TRUE.equals(offline));

            Fragment current = getSupportFragmentManager().findFragmentById(R.id.mainContainer);
            if (current instanceof SituationListFragment) {
                ((SituationListFragment) current).updateData(allSituations);
            } else if (current instanceof TrafficMapFragment) {
                ((TrafficMapFragment) current).updateMarkers(allSituations);
            }

            if (isTwoPaneLandscape && !allSituations.isEmpty()
                    && viewModel.getSelectedSituation().getValue() == null) {
                Situation first = allSituations.get(0);
                viewModel.selectSituation(first);
                showDetailInLandscape(first);
            }
        });
    }

    /**
     * Updates the status banner showing online last refreshed time or offline alert.
     */
    private void updateStatusBanner(boolean isOnline) {
        if (offlineBanner == null) return;
        offlineBanner.setVisibility(View.VISIBLE);

        if (isOnline) {
            String time = new SimpleDateFormat("HH:mm", Locale.UK).format(new Date());
            offlineBanner.setBackgroundColor(Color.parseColor("#2E7D32"));
            offlineBanner.setText("● Online • Last refreshed: " + time);
        } else {
            offlineBanner.setBackgroundColor(Color.parseColor("#C62828"));
            offlineBanner.setText("● Offline • Showing cached data");
        }
    }

    /**
     * Replaces the main container fragment.
     */
    private void loadMainFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.mainContainer, fragment)
                .commit();
    }

    /**
     * Callback interface method called when a situation item is selected from a list.
     */
    @Override
    public void onSituationSelected(Situation situation) {
        viewModel.selectSituation(situation);

        if (isTwoPaneLandscape) {
            showDetailInLandscape(situation);
        } else {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.mainContainer, SituationDetailFragment.newInstance(situation))
                    .addToBackStack(null)
                    .commit();
        }
    }

    /**
     * Loads situation details into the dedicated right pane in dual-pane landscape orientation.
     */
    private void showDetailInLandscape(Situation situation) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.detailContainer, SituationDetailFragment.newInstance(situation))
                .commit();
    }

    /**
     * Switches bottom navigation to Map tab and displays a filtered list of situations on the map.
     */
    public void showFilteredOnMap(ArrayList<Situation> filteredList) {
        if (bottomNavigation != null) {
            bottomNavigation.getMenu().findItem(R.id.nav_map).setChecked(true);
        }
        ArrayList<Situation> listToShow = (filteredList != null) ? filteredList : allSituations;
        loadMainFragment(TrafficMapFragment.newInstance(listToShow));
    }
}