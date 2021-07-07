package ru.danilakondratenko.incubatorcontrol;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class ArchiveActivity extends AppCompatActivity {
    /* Timespan indexes */

    private static final int TIMESPAN_CURRENT = 0;
    private static final int TIMESPAN_LAST_HOUR = 1;
    private static final int TIMESPAN_LAST_DAY = 2;
    private static final int TIMESPAN_LAST_WEEK = 3;
    private static final int TIMESPAN_LAST_MONTH = 4;
    private static final int TIMESPAN_LAST_YEAR = 5;

    /* Timespan length */

    public static final long MINUTE = 60000L;
    public static final long HOUR   = 3600000L;
    public static final long DAY    = HOUR*24L;
    public static final long WEEK   = DAY*7L;
    public static final long MONTH  = DAY*30L;
    public static final long YEAR   = DAY*365L;

    /* Archive state position */

    public static final double WETTER_OFF  = 0;
    public static final double WETTER_ON   = WETTER_OFF + 1;
    public static final double HEATER_OFF  = 0;
    public static final double HEATER_ON   = HEATER_OFF + 1;

    public static final double CHAMBER_NEUTRAL = 0;
    public static final double CHAMBER_LEFT    = CHAMBER_NEUTRAL - 1;
    public static final double CHAMBER_RIGHT   = CHAMBER_NEUTRAL + 1;

    /* GraphView label parameters */

    public static final int NUM_VERTICAL = 5;
    public static final int NUM_HORIZONTAL = 3;

    private static final String LOG_TAG = "Archive";

    public static final String DEFAULT_INCUBATOR_ADDRESS = "incubator.local";
    public static final String DEFAULT_ARCHIVE_ADDRESS = "185.26.121.126";

    Spinner spTimespan;

    GraphView gvTempGraph;
    LineGraphSeries<DataPoint> currentTempSeries;
    LineGraphSeries<DataPoint> neededTempSeries;
    LineGraphSeries<DataPoint> heaterSeries;

    GraphView gvHumidGraph;
    LineGraphSeries<DataPoint> currentHumidSeries;
    LineGraphSeries<DataPoint> neededHumidSeries;
    LineGraphSeries<DataPoint> wetterSeries;

    GraphView gvChamberGraph;
    LineGraphSeries<DataPoint> chamberSeries;

    Handler hGraph;
    Runnable rGraphUpdate;

    SharedPreferences prefs;
    SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    Archiver archiver;

    String incubatorAddress = DEFAULT_INCUBATOR_ADDRESS;
    String archiveAddress = DEFAULT_ARCHIVE_ADDRESS;
    private boolean cloudArchiveMode = false;

    private void requestArchiveAddress() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://" + incubatorAddress)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();
        IncubatorRequest request = retrofit.create(IncubatorRequest.class);
        Call<String> call = request.getArchiveAddress();
        call.enqueue(new Callback<String>() {

            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response.body() != null)
                    archiveAddress = response.body().trim();
                else
                    archiveAddress = DEFAULT_ARCHIVE_ADDRESS;
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {

            }
        });
    }

    private long timespanBegin(int timespan_type) {
        long result = 0;
        Calendar calendar = Calendar.getInstance();

        switch (timespan_type) {
            case TIMESPAN_LAST_HOUR:
                result = calendar.getTimeInMillis() - HOUR;
                break;
            case TIMESPAN_LAST_DAY:
                result = calendar.getTimeInMillis() - DAY;
                break;
            case TIMESPAN_LAST_WEEK:
                result = calendar.getTimeInMillis() - WEEK;
                break;
            case TIMESPAN_LAST_MONTH:
                result = calendar.getTimeInMillis() - MONTH;
                break;
            case TIMESPAN_LAST_YEAR:
                result = calendar.getTimeInMillis() - YEAR;
                break;
            case TIMESPAN_CURRENT:
                result = calendar.getTimeInMillis() - MINUTE;
                break;
        }

        return result;
    }

    private void setTempGraphData(
            ArrayList<DataPoint> alCurrentTemps, ArrayList<DataPoint> alNeededTemps,
            ArrayList<DataPoint> alHeaterStates, long min_time, long max_time) {
        DataPoint[] dpaCurrentTemps = new DataPoint[alCurrentTemps.size()];
        DataPoint[] dpaNeededTemps = new DataPoint[alNeededTemps.size()];
        DataPoint[] dpaHeaterStates = new DataPoint[alHeaterStates.size()];

        alCurrentTemps.toArray(dpaCurrentTemps);
        alNeededTemps.toArray(dpaNeededTemps);
        alHeaterStates.toArray(dpaHeaterStates);

        currentTempSeries.resetData(dpaCurrentTemps);
        neededTempSeries.resetData(dpaNeededTemps);
        heaterSeries.resetData(dpaHeaterStates);

        gvTempGraph.getViewport().setXAxisBoundsManual(true);
        gvTempGraph.getViewport().setMinX(min_time);
        gvTempGraph.getViewport().setMaxX(max_time);
    }

    private void setHumidGraphData(
            ArrayList<DataPoint> alCurrentHumids, ArrayList<DataPoint> alNeededHumids,
            ArrayList<DataPoint> alWetterStates, long min_time, long max_time) {
        DataPoint[] dpaCurrentHumids = new DataPoint[alCurrentHumids.size()];
        DataPoint[] dpaNeededHumids = new DataPoint[alNeededHumids.size()];
        DataPoint[] dpaWetterStates = new DataPoint[alWetterStates.size()];

        alCurrentHumids.toArray(dpaCurrentHumids);
        alNeededHumids.toArray(dpaNeededHumids);
        alWetterStates.toArray(dpaWetterStates);

        currentHumidSeries.resetData(dpaCurrentHumids);
        neededHumidSeries.resetData(dpaNeededHumids);
        wetterSeries.resetData(dpaWetterStates);

        gvHumidGraph.getViewport().setXAxisBoundsManual(true);
        gvHumidGraph.getViewport().setMinX(min_time);
        gvHumidGraph.getViewport().setMaxX(max_time);
    }

    private void setChamberGraphData(
            ArrayList<DataPoint> alChamberStates, long min_time, long max_time) {
        DataPoint[] dpaChamberStates = new DataPoint[alChamberStates.size()];

        alChamberStates.toArray(dpaChamberStates);

        chamberSeries.resetData(dpaChamberStates);

        gvChamberGraph.getViewport().setXAxisBoundsManual(true);
        gvChamberGraph.getViewport().setMinX(min_time);
        gvChamberGraph.getViewport().setMaxX(max_time);
    }

    void scanRecords_local(int timespan_type) {
        try {
            File archive = archiver.getLocalArchiveFile();
            archive.setReadable(true);

            FileInputStream istream = new FileInputStream(archive);
            byte[] buf = new byte[Archiver.RECORD_SIZE];

            ArrayList<DataPoint> currentTemps = new ArrayList<>();
            ArrayList<DataPoint> neededTemps = new ArrayList<>();
            ArrayList<DataPoint> currentHumids = new ArrayList<>();
            ArrayList<DataPoint> neededHumids = new ArrayList<>();
            ArrayList<DataPoint> heaterStates = new ArrayList<>();
            ArrayList<DataPoint> wetterStates = new ArrayList<>();
            ArrayList<DataPoint> chamberStates = new ArrayList<>();

            long min_time = Long.MAX_VALUE, max_time = Long.MIN_VALUE;

            ArchiveRecord record;

            long timespan_begin = timespanBegin(timespan_type);

            while (istream.read(buf) != -1) {
                record = archiver.getArchiveRecordFromBytes(buf);

                if (record.timestamp < timespan_begin)
                    continue;

                if (record.timestamp <= min_time)
                    min_time = record.timestamp;

                if (record.timestamp >= max_time)
                    max_time = record.timestamp;

                currentTemps.add(new DataPoint(record.timestamp, record.currentTemperature));
                currentHumids.add(new DataPoint(record.timestamp, record.currentHumidity));

                neededTemps.add(new DataPoint(record.timestamp, record.neededTemperature));
                neededHumids.add(new DataPoint(record.timestamp, record.neededHumidity));

                currentTemps.add(new DataPoint(record.timestamp, record.currentTemperature));
                currentHumids.add(new DataPoint(record.timestamp, record.currentHumidity));
                neededTemps.add(new DataPoint(record.timestamp, record.neededTemperature));
                neededHumids.add(new DataPoint(record.timestamp, record.neededHumidity));
                heaterStates.add(new DataPoint(record.timestamp,
                        record.heater*HEATER_ON + HEATER_OFF));
                wetterStates.add(new DataPoint(record.timestamp,
                        record.wetter*WETTER_ON + WETTER_OFF));
                chamberStates.add(new DataPoint(record.timestamp, CHAMBER_NEUTRAL + record.chamber));
            }

            setTempGraphData(currentTemps, neededTemps, heaterStates, min_time, max_time);
            setHumidGraphData(currentHumids, neededHumids, wetterStates, min_time, max_time);
            setChamberGraphData(chamberStates, min_time, max_time);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    void scanRecords_cloud(int timespan_type) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://" + archiveAddress)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        ArchiveRequest archiveRequest = retrofit.create(ArchiveRequest.class);
        Call<ArchiveRecord[]> call = archiveRequest.getArchive(timespanBegin(timespan_type));
        call.enqueue(new Callback<ArchiveRecord[]>() {
            @Override
            public void onResponse(Call<ArchiveRecord[]> call, Response<ArchiveRecord[]> response) {
                ArrayList<DataPoint> currentTemps = new ArrayList<>();
                ArrayList<DataPoint> neededTemps = new ArrayList<>();
                ArrayList<DataPoint> currentHumids = new ArrayList<>();
                ArrayList<DataPoint> neededHumids = new ArrayList<>();
                ArrayList<DataPoint> heaterStates = new ArrayList<>();
                ArrayList<DataPoint> wetterStates = new ArrayList<>();
                ArrayList<DataPoint> chamberStates = new ArrayList<>();

                long min_time = Long.MAX_VALUE;
                long max_time = Long.MIN_VALUE;

                for (ArchiveRecord record : response.body()) {
                    if (record.timestamp <= min_time)
                        min_time = record.timestamp;

                    if (record.timestamp >= max_time)
                        max_time = record.timestamp;

                    int chamber = record.chamber;
                    if (chamber == IncubatorState.CHAMBER_UNDEF)
                        chamber = IncubatorState.CHAMBER_RIGHT;
                    currentTemps.add(new DataPoint(record.timestamp, record.currentTemperature));
                    currentHumids.add(new DataPoint(record.timestamp, record.currentHumidity));
                    neededTemps.add(new DataPoint(record.timestamp, record.neededTemperature));
                    neededHumids.add(new DataPoint(record.timestamp, record.neededHumidity));
                    heaterStates.add(new DataPoint(record.timestamp,
                            record.heater*HEATER_ON + HEATER_OFF));
                    wetterStates.add(new DataPoint(record.timestamp,
                            record.wetter*WETTER_ON + WETTER_OFF));
                    chamberStates.add(new DataPoint(record.timestamp, CHAMBER_NEUTRAL + chamber));
                }

                setTempGraphData(currentTemps, neededTemps, heaterStates, min_time, max_time);
                setHumidGraphData(currentHumids, neededHumids, wetterStates, min_time, max_time);
                setChamberGraphData(chamberStates, min_time, max_time);
            }

            @Override
            public void onFailure(Call<ArchiveRecord[]> call, Throwable t) {

            }
        });
    }

    void scanRecords(int timespan_type) {
        if (cloudArchiveMode) {
            scanRecords_cloud(timespan_type);
        } else {
            scanRecords_local(timespan_type);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive);

        archiver = new Archiver(getApplicationContext());

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        incubatorAddress = prefs.getString("incubator_address", DEFAULT_INCUBATOR_ADDRESS);
        cloudArchiveMode = prefs.getBoolean("cloud_archive_mode", true);
        prefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                Log.i(LOG_TAG, "sharedPreferenceChanged@" + key);
                if (key.compareTo("incubator_address") == 0) {
                    incubatorAddress = sharedPreferences.getString(
                            key, DEFAULT_INCUBATOR_ADDRESS
                    );
                    if (cloudArchiveMode)
                        requestArchiveAddress();
                } else if (key.compareTo("cloud_archive_mode") == 0) {
                    cloudArchiveMode = sharedPreferences.getBoolean("cloud_archive_mode", true);
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        if (cloudArchiveMode)
            requestArchiveAddress();

        ArrayAdapter<CharSequence> adapter =
                ArrayAdapter.createFromResource(
                        this, R.array.timespan_list, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spTimespan = findViewById(R.id.timespan);
        spTimespan.setAdapter(adapter);
        spTimespan.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                hGraph.removeCallbacks(rGraphUpdate);
                if (position == TIMESPAN_CURRENT) {
                    hGraph.postDelayed(rGraphUpdate, IncubatorStateActivity.REQ_TIMEOUT);
                }
                scanRecords(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        gvTempGraph = findViewById(R.id.tempGraph);
        gvTempGraph.getViewport().setScalable(true);
        gvTempGraph.getGridLabelRenderer().setHumanRounding(false);
        gvTempGraph.getGridLabelRenderer().setLabelFormatter(
                new DateAsXAxisLabelFormatter(this, DateFormat.getTimeInstance()));
        gvTempGraph.getGridLabelRenderer().setNumHorizontalLabels(NUM_HORIZONTAL);
        gvTempGraph.getGridLabelRenderer().setNumVerticalLabels(NUM_VERTICAL);
        gvTempGraph.getLegendRenderer().setVisible(true);
        gvTempGraph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
        gvTempGraph.getSecondScale().setMinY(HEATER_OFF);
        gvTempGraph.getSecondScale().setMaxY(HEATER_ON);

        currentTempSeries = new LineGraphSeries<>();
        currentTempSeries.setColor(Color.BLUE);
        currentTempSeries.setTitle(getString(R.string.current_temperature));
        gvTempGraph.addSeries(currentTempSeries);

        neededTempSeries = new LineGraphSeries<>();
        neededTempSeries.setColor(Color.argb(255, 0, 0, 127)); // Dark blue
        neededTempSeries.setTitle(getString(R.string.needed_temperature));
        gvTempGraph.addSeries(neededTempSeries);

        heaterSeries = new LineGraphSeries<>();
        heaterSeries.setColor(Color.RED);
        heaterSeries.setTitle(getString(R.string.heater_state));
        gvTempGraph.getSecondScale().addSeries(heaterSeries);

        gvHumidGraph = findViewById(R.id.humidGraph);
        gvHumidGraph.getViewport().setScalable(true);
        gvHumidGraph.getGridLabelRenderer().setHumanRounding(false);
        gvHumidGraph.getGridLabelRenderer().setLabelFormatter(
                new DateAsXAxisLabelFormatter(this, DateFormat.getTimeInstance()));
        gvHumidGraph.getGridLabelRenderer().setNumHorizontalLabels(NUM_HORIZONTAL);
        gvHumidGraph.getGridLabelRenderer().setNumVerticalLabels(NUM_VERTICAL);
        gvHumidGraph.getLegendRenderer().setVisible(true);
        gvHumidGraph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
        gvHumidGraph.getSecondScale().setMinY(WETTER_OFF);
        gvHumidGraph.getSecondScale().setMaxY(WETTER_ON);

        currentHumidSeries = new LineGraphSeries<>();
        currentHumidSeries.setColor(Color.GREEN);
        currentHumidSeries.setTitle(getString(R.string.current_humidity));
        gvHumidGraph.addSeries(currentHumidSeries);

        neededHumidSeries = new LineGraphSeries<>();
        neededHumidSeries.setColor(Color.argb(255, 0, 127, 0)); // Dark green
        neededHumidSeries.setTitle(getString(R.string.needed_humidity));
        gvHumidGraph.addSeries(neededHumidSeries);

        wetterSeries = new LineGraphSeries<>();
        wetterSeries.setColor(Color.CYAN);
        wetterSeries.setTitle(getString(R.string.wetter_state));
        gvHumidGraph.getSecondScale().addSeries(wetterSeries);

        gvChamberGraph = findViewById(R.id.chamberGraph);
        gvChamberGraph.getViewport().setScalable(true);
        gvChamberGraph.getViewport().setYAxisBoundsManual(true);
        gvChamberGraph.getViewport().setMinY(CHAMBER_LEFT);
        gvChamberGraph.getViewport().setMaxY(CHAMBER_RIGHT);
        gvChamberGraph.getGridLabelRenderer().setHumanRounding(false);
        gvChamberGraph.getGridLabelRenderer().setLabelFormatter(
                new DateAsXAxisLabelFormatter(this, DateFormat.getTimeInstance()));
        gvChamberGraph.getGridLabelRenderer().setNumHorizontalLabels(NUM_HORIZONTAL);
        gvChamberGraph.getGridLabelRenderer().setNumVerticalLabels(NUM_VERTICAL);
        gvChamberGraph.getLegendRenderer().setVisible(true);
        gvChamberGraph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);

        chamberSeries = new LineGraphSeries<>();
        chamberSeries.setColor(Color.LTGRAY);
        chamberSeries.setTitle(getString(R.string.chamber_pos));
        gvChamberGraph.addSeries(chamberSeries);

        hGraph = new Handler(Looper.myLooper());

        rGraphUpdate = new Runnable() {
            @Override
            public void run() {
                scanRecords(TIMESPAN_CURRENT);
                hGraph.postDelayed(this, IncubatorStateActivity.REQ_TIMEOUT);
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();

        scanRecords(TIMESPAN_CURRENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hGraph.postDelayed(rGraphUpdate, IncubatorStateActivity.REQ_TIMEOUT);
    }

    @Override
    protected void onPause() {
        hGraph.removeCallbacks(rGraphUpdate);
        super.onPause();
    }

    public void onUpdateBtnClick(View view) {
        scanRecords(spTimespan.getSelectedItemPosition());
    }
}