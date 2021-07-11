package ru.danilakondratenko.incubatorcontrol;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.VideoView;

import java.util.Timer;
import java.util.TimerTask;

public class IncubatorVideoActivity extends AppCompatActivity {
    private static final String LOG_TAG = "Video";

    String videoAddress;

    Requestor requestor;

    VideoView videoView;
    Button btnToggleLights;

    SharedPreferences prefs;
    SharedPreferences.OnSharedPreferenceChangeListener listener;

    Button btnVideoArchive;

    private Timer lightsTimer;
    private boolean lights;

    void updateLights() {
        if (lights) {
            btnToggleLights.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
            btnToggleLights.setText(R.string.turn_lights_off);
        } else {
            btnToggleLights.setBackgroundColor(getResources().getColor(R.color.purple_700));
            btnToggleLights.setText(R.string.turn_lights_on);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incubator_video);

        requestor = new Requestor(getApplicationContext());
        videoAddress = requestor.getIncubatorAddress() + ":8081";
        videoView = findViewById(R.id.incubatorVideo);
        btnToggleLights = findViewById(R.id.toggleLightsButton);
        btnToggleLights.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestor.sendLightsState(!lights);
                lights = !lights;
                updateLights();
            }
        });
        btnVideoArchive = findViewById(R.id.viewArchiveButton);
        btnVideoArchive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(
                        IncubatorVideoActivity.this, IncubatorVideoArchiveActivity.class
                );
                startActivity(intent);
            }
        });

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.compareTo("incubator_address") == 0) {
                    videoView.stopPlayback();
                    videoAddress =
                            sharedPreferences.getString(key, Requestor.DEFAULT_INCUBATOR_ADDRESS)
                                    + ":8081";

                    videoView.setVideoURI(Uri.parse("http://" + videoAddress + "/"));
                    videoView.start();
                }
            }
        };

        videoView.setVideoURI(Uri.parse("http://" + videoAddress + "/"));
        videoView.start();

        lightsTimer = new Timer("IncubatorVideoActivity LightsTimer");
        lightsTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                requestor.requestLightsState(new RequestCallback() {
                    @Override
                    public void onAnswer(String answer) {
                        for (String line : answer.replace("\r\n", "\n").split("\n")) {
                            if (line.compareTo("lights_on") == 0)
                                lights = true;
                            else if (line.compareTo("lights_off") == 0)
                                lights = false;
                        }
                        updateLights();
                    }

                    @Override
                    public void onFailure() {

                    }
                });
            }
        }, 0, Requestor.REQ_TIMEOUT);

    }

    @Override
    protected void onResume() {
        super.onResume();
        videoView.stopPlayback();
        videoView.start();
    }
}