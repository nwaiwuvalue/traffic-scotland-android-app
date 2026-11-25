package org.me.gcu.nwaiwu_value_s2358062;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;

/**
 * TrafficViewModel manages UI-related data lifecycle and connects UI fragments to TrafficRepository.
 */
public class TrafficViewModel extends AndroidViewModel {

    private final TrafficRepository repository;

    private final MutableLiveData<ArrayList<Situation>> situations = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isFromCache = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);
    private final MutableLiveData<Situation> selectedSituation = new MutableLiveData<>(null);

    public TrafficViewModel(@NonNull Application application) {
        super(application);
        repository = new TrafficRepository(application.getApplicationContext());
    }

    public LiveData<ArrayList<Situation>> getSituations() {
        return situations;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<Boolean> getIsFromCache() {
        return isFromCache;
    }

    public LiveData<Boolean> getIsOffline() {
        return isFromCache;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Situation> getSelectedSituation() {
        return selectedSituation;
    }

    public void selectSituation(Situation situation) {
        selectedSituation.setValue(situation);
    }

    /**
     * Triggers repository data refresh and updates LiveData observers.
     */
    public void refreshData() {
        isLoading.setValue(true);
        errorMessage.setValue(null);

        repository.fetchAllSituations(new TrafficRepository.TrafficCallback() {
            @Override
            public void onSuccess(ArrayList<Situation> result, boolean fromCache) {
                isLoading.setValue(false);
                isFromCache.setValue(fromCache);
                situations.setValue(result);
            }

            @Override
            public void onError(String message) {
                isLoading.setValue(false);
                errorMessage.setValue(message);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.shutdown();
    }
}