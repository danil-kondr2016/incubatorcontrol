package ru.danilakondratenko.incubatorcontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class Requestor {
    public static final String DEFAULT_INCUBATOR_ADDRESS = "incubator.local";
    public static final long REQ_TIMEOUT = 2000;

    public static final int ROTATE_LEFT = -1;
    public static final int ROTATE_RIGHT = 1;
    public static final int ROTATE_OFF = 0;

    private static final String LOG_TAG = "Requestor";

    Context context;
    private String incubatorAddress;

    private ExecutorService executor;
    private IncubatorRequest requestAPI;

    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    Requestor(Context context) {
        this.context = context;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
        this.listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.compareTo("incubator_address") == 0) {
                    Requestor.this.incubatorAddress =
                            sharedPreferences.getString(key, DEFAULT_INCUBATOR_ADDRESS);
                    buildRequestAPI();
                }
            }
        };
        this.prefs.registerOnSharedPreferenceChangeListener(this.listener);

        this.incubatorAddress = prefs.getString("incubator_address", DEFAULT_INCUBATOR_ADDRESS);
        buildRequestAPI();

        this.executor = Executors.newSingleThreadExecutor();
    }

    private void buildRequestAPI() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://" + incubatorAddress)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();

        this.requestAPI = retrofit.create(IncubatorRequest.class);
    }

    public String getIncubatorAddress() {
        return this.incubatorAddress;
    }

    private void sendCommand(String request) {
        Call<String> call = requestAPI.getResponse(request);
        call.timeout().deadline(REQ_TIMEOUT, TimeUnit.MILLISECONDS);
        call.enqueue(new retrofit2.Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {

            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }

    private void sendCommand(String request, RequestCallback callback) {
        Call<String> call = requestAPI.getResponse(request);
        call.timeout().deadline(REQ_TIMEOUT, TimeUnit.MILLISECONDS);
        call.enqueue(new retrofit2.Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                callback.onAnswer(response.body());
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                t.printStackTrace();
                callback.onFailure();
            }
        });
    }

    public void requestState(RequestCallback callback) {
        sendCommand("request_state\r\n", callback);
    }

    public void requestConfig(RequestCallback callback) {
        sendCommand("request_config\r\n", callback);
    }

    public void sendConfig(IncubatorConfig cfg) {
        sendCommand(String.format(Locale.US, "needed_temp %.2f\r\n", cfg.neededTemperature));
        sendCommand(String.format(Locale.US, "needed_humid %.2f\r\n", cfg.neededHumidity));
        sendCommand(String.format(Locale.US, "rotations_per_day %d\r\n", cfg.rotationsPerDay));
        sendCommand(String.format(Locale.US, "switch_to_program %d\r\n", cfg.currentProgram));
    }

    public void requestLightsState(RequestCallback callback) {
        sendCommand("lights_state\r\n", callback);
    }

    public void sendLightsState(boolean state) {
        if (state)
            sendCommand("lights_on\r\n");
        else
            sendCommand("lights_off\r\n");
    }

    public void sendRotationCommand(int rotationCommand) {
        switch (rotationCommand) {
            case ROTATE_LEFT:
                sendCommand("rotate_left\r\n");
                break;
            case ROTATE_RIGHT:
                sendCommand("rotate_right\r\n");
                break;
            case ROTATE_OFF:
                sendCommand("rotate_off\r\n");
        }
    }
}
