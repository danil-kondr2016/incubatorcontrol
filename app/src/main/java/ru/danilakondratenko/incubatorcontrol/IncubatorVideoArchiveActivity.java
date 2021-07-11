package ru.danilakondratenko.incubatorcontrol;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class IncubatorVideoArchiveActivity extends AppCompatActivity {
    public static final long VIDEO_ARCHIVE_TIMEOUT = 60000;
    private static final String LOG_TAG = "VideoArchive";

    AtomicBoolean readyToPlay;

    String videoArchiveAddress;

    Requestor requestor;
    ExecutorService executor;

    PlayerView videoView;
    SimpleExoPlayer player;
    Spinner spVideoList;

    ImageButton ibPreviousVideo;
    ImageButton ibNextVideo;

    Timer archiveTimer;

    SharedPreferences prefs;
    SharedPreferences.OnSharedPreferenceChangeListener listener;

    ArrayList<String> videoList;

    void getVideoArchiveList(ArrayList<String> videoList) {
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
        ArrayList<String> result = new ArrayList<>();

        try {
            String answer = future.get();
            Log.i(LOG_TAG, answer);
            answerStrings = answer.split("\n");
            videoList.clear();
            videoList.addAll(Arrays.asList(answerStrings));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    boolean updateVideoList() {
        DateFormat format = DateFormat.getDateTimeInstance();
        getVideoArchiveList(videoList);
        if (videoList.size() == 0)
            return false;
        ArrayList<String> dateNameList = new ArrayList<>();
        for (String video : videoList) {
            if (video.length() == 0)
                return false;
            long time = Long.parseLong(video) * 1000;
            dateNameList.add(format.format(new Date(time)));
        }

        int index = spVideoList.getSelectedItemPosition();

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(IncubatorVideoArchiveActivity.this,
                        android.R.layout.simple_spinner_item, dateNameList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spVideoList.setAdapter(adapter);
        spVideoList.setSelection(index);

        return true;
    }

    void playVideo(String name) {
        MediaItem item = MediaItem.fromUri(
                Uri.parse("http://" + videoArchiveAddress + "/" + name));
        player.setMediaItem(item);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    void playVideo(int index) {
        String name = (String)spVideoList.getItemAtPosition(index);
        playVideo(name);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incubator_video_archive);

        readyToPlay = new AtomicBoolean(false);
        videoList = new ArrayList<>();

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
                    readyToPlay.set(updateVideoList());
                }
            }
        };

        videoArchiveAddress =
                prefs.getString("incubator_address", Requestor.DEFAULT_INCUBATOR_ADDRESS) +
                        ":8080";

        readyToPlay.set(updateVideoList());
        spVideoList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    if (!readyToPlay.get())
                        return;
                    DateFormat format = DateFormat.getDateTimeInstance();
                    String selectedVideo =
                            String.valueOf(
                                    format.parse(
                                            (String) parent.getSelectedItem()).getTime() / 1000);

                    runOnUiThread(() -> playVideo(selectedVideo));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        ibPreviousVideo = findViewById(R.id.previousVideoButton);
        ibPreviousVideo.setOnClickListener((v) -> {
            if (spVideoList.getSelectedItemPosition() - 1 >= 0)
                spVideoList.setSelection(spVideoList.getSelectedItemPosition() - 1);
        });

        ibNextVideo = findViewById(R.id.nextVideoButton);
        ibNextVideo.setOnClickListener((v) -> {
            if (spVideoList.getSelectedItemPosition() + 1 < videoList.size())
                spVideoList.setSelection(spVideoList.getSelectedItemPosition() + 1);
        });

        archiveTimer = new Timer("IncubatorVideoArchiveActivity Timer");
        archiveTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    readyToPlay.set(updateVideoList());
                });
            }
        }, 0, VIDEO_ARCHIVE_TIMEOUT);

        playVideo(videoList.size() - 1);
    }
}