package ru.danilakondratenko.incubatorcontrol;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.VideoView;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class IncubatorVideoArchiveActivity extends AppCompatActivity {
    String videoArchiveAddress;

    Requestor requestor;
    ExecutorService executor;

    PlayerView videoView;
    SimpleExoPlayer player;
    Spinner spVideoList;

    Timer archiveTimer;

    SharedPreferences prefs;
    SharedPreferences.OnSharedPreferenceChangeListener listener;

    String[] videoList;

    String[] getVideoArchiveList() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://" + videoArchiveAddress)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();
        VideoArchiveRequest request = retrofit.create(VideoArchiveRequest.class);
        Call<String> call = request.getArchiveList();
        Future<String> future = executor.submit(new Callable<String>() {

            @Override
            public String call() throws Exception {
                return call.execute().body();
            }
        });
        String[] answerStrings;

        try {
            answerStrings = future.get().split("\n");
            return answerStrings;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    void updateVideoList() {
        DateFormat format = DateFormat.getDateTimeInstance();
        videoList = getVideoArchiveList();
        String[] dateNameList = new String[videoList.length];
        for (int i = 0; i < videoList.length; i++) {
            String video = videoList[i];
            long time = Long.parseLong(video) * 1000;
            dateNameList[i] = format.format(new Date(time));
        }
        ArrayAdapter<CharSequence> adapter =
                new ArrayAdapter<>(IncubatorVideoArchiveActivity.this,
                        android.R.layout.simple_spinner_item, dateNameList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spVideoList.setAdapter(adapter);
    }

    void playVideo(String name) {
        MediaItem item = MediaItem.fromUri(
                Uri.parse("http://" + videoArchiveAddress + "/" + name));
        player.setMediaItem(item);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incubator_video_archive);

        spVideoList = findViewById(R.id.videoArchiveList);
        videoView = findViewById(R.id.videoArchiveView);
        player = new SimpleExoPlayer.Builder(this).build();
        videoView.setPlayer(player);

        requestor = new Requestor(getApplicationContext());
        executor = Executors.newSingleThreadExecutor();

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.compareTo("incubator_address") == 0) {
                    player.stop();
                    videoArchiveAddress =
                            sharedPreferences.getString(key, Requestor.DEFAULT_INCUBATOR_ADDRESS) +
                                    ":8080";
                    updateVideoList();
                }
            }
        };

        videoArchiveAddress =
                prefs.getString("incubator_address", Requestor.DEFAULT_INCUBATOR_ADDRESS) +
                        ":8080";

        updateVideoList();
        spVideoList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    DateFormat format = DateFormat.getDateTimeInstance();
                    String selectedVideo =
                            String.valueOf(
                                    format.parse(
                                            (String) parent.getSelectedItem()).getTime() / 1000);
                    if (selectedVideo == null)
                        return;

                    runOnUiThread(() -> playVideo(selectedVideo));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        archiveTimer = new Timer("IncubatorVideoArchiveActivity Timer");
        archiveTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                while (videoList.length == 0) {
                    runOnUiThread(() -> updateVideoList());
                }
            }
        }, 0, 60000);

        playVideo(videoList[0]);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateVideoList();
    }
}