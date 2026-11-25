package org.me.gcu.nwaiwu_value_s2358062;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository class handling network requests to Traffic Scotland REST APIs and local caching via SharedPreferences.
 */
public class TrafficRepository {

    private static final String TAG = "TrafficRepository";

    private static final String UNPLANNED_URL =
            "https://datex2.trafficscotland.org/rest/2.3/publications/UnplannedEvents/Content.xml";
    private static final String ROADWORKS_URL =
            "https://datex2.trafficscotland.org/rest/2.3/publications/CurrentRoadworks/Content.xml";

    private static final String PREFS_NAME = "traffic_scotland_cache";
    private static final String KEY_UNPLANNED_XML = "cached_unplanned_xml";
    private static final String KEY_ROADWORKS_XML = "cached_roadworks_xml";

    private final SharedPreferences prefs;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    /**
     * Callback interface to return asynchronous fetch results to callers.
     */
    public interface TrafficCallback {
        void onSuccess(ArrayList<Situation> situations, boolean isFromCache);
        void onError(String message);
    }

    public TrafficRepository(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Fetches both DATEX II feeds on a background Executor thread.
     * Caches fresh XML on success, or falls back to cached XML when offline.
     */
    public void fetchAllSituations(TrafficCallback callback) {
        executorService.execute(() -> {
            boolean usedCache = false;

            String unplannedXml = downloadXml(UNPLANNED_URL);
            String roadworksXml = downloadXml(ROADWORKS_URL);

            if (unplannedXml != null && roadworksXml != null) {
                // Save XML to local cache for offline browsing
                prefs.edit()
                        .putString(KEY_UNPLANNED_XML, unplannedXml)
                        .putString(KEY_ROADWORKS_XML, roadworksXml)
                        .apply();
            } else {
                // Offline fallback to SharedPreferences cache
                unplannedXml = prefs.getString(KEY_UNPLANNED_XML, null);
                roadworksXml = prefs.getString(KEY_ROADWORKS_XML, null);
                usedCache = true;
            }

            if (unplannedXml == null && roadworksXml == null) {
                postError(callback, "No internet connection and no cached data available.");
                return;
            }

            // Parse XML strings on background thread
            ArrayList<Situation> combined = new ArrayList<>();
            if (unplannedXml != null) {
                combined.addAll(DatexPullParser.parse(unplannedXml, "Unplanned Event"));
            }
            if (roadworksXml != null) {
                combined.addAll(DatexPullParser.parse(roadworksXml, "Current Roadworks"));
            }

            final boolean finalUsedCache = usedCache;
            mainHandler.post(() -> callback.onSuccess(combined, finalUsedCache));
        });
    }

    /**
     * Downloads XML data from the specified URL using HttpURLConnection and HTTP Basic Authentication.
     */
    private String downloadXml(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("GET");

            String credentials = BuildConfig.DATEX_CLIENT_ID + ":" + BuildConfig.DATEX_CLIENT_KEY;
            String encodedAuth = Base64.getEncoder().encodeToString(credentials.getBytes());
            connection.setRequestProperty("Authorization", encodedAuth);

            int statusCode = connection.getResponseCode();
            if (statusCode == HttpURLConnection.HTTP_OK) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                return sb.toString();
            } else {
                Log.e(TAG, "HTTP Error " + statusCode + " for " + urlString);
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Network error for " + urlString + ": " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void postError(TrafficCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    public void shutdown() {
        executorService.shutdownNow();
    }
}