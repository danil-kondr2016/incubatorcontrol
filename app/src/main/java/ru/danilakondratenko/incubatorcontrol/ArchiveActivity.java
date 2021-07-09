package ru.danilakondratenko.incubatorcontrol;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class ArchiveActivity extends AppCompatActivity {
    public static final long REQ_TIMEOUT = 2000;

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

    public static final double WETTER_HEIGHT = 1;
    public static final double WETTER_OFF    = 0;
    public static final double WETTER_ON     = WETTER_OFF + WETTER_HEIGHT;

    public static final double HEATER_HEIGHT = 1;
    public static final double HEATER_OFF    = 0;
    public static final double HEATER_ON     = HEATER_OFF + HEATER_HEIGHT;

    public static final double CHAMBER_HEIGHT  = 1;
    public static final double CHAMBER_NEUTRAL = 0;
    public static final double CHAMBER_LEFT    = CHAMBER_NEUTRAL - CHAMBER_HEIGHT;
    public static final double CHAMBER_RIGHT   = CHAMBER_NEUTRAL + CHAMBER_HEIGHT;

    /* GraphView label parameters */

    public static final int NUM_VERTICAL = 5;
    public static final int NUM_HORIZONTAL = 3;

    private static final String LOG_TAG = "Archive";

    public static final String DEFAULT_INCUBATOR_ADDRESS = "incubator.local";

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
    private boolean cloudArchiveMode = false;

    float clearFloat(float x) {
        short xa = (short)(x * 256);
        return xa / 256.0f;
    }

    long roundTimeFloor(long x) {
        long milliseconds = x % 1000L;
        return x - milliseconds;
    }

    long roundTimeCeiling(long x) {
        long milliseconds = x % 1000L;
        return x + (1000L - milliseconds);
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

    void scanArchiveData(ArchiveRecord[] records) {
        ArrayList<DataPoint> currentTemps = new ArrayList<>();
        ArrayList<DataPoint> neededTemps = new ArrayList<>();
        ArrayList<DataPoint> currentHumids = new ArrayList<>();
        ArrayList<DataPoint> neededHumids = new ArrayList<>();
        ArrayList<DataPoint> heaterStates = new ArrayList<>();
        ArrayList<DataPoint> wetterStates = new ArrayList<>();
        ArrayList<DataPoint> chamberStates = new ArrayList<>();

        long min_time = Long.MAX_VALUE;
        long max_time = Long.MIN_VALUE;

        for (ArchiveRecord record : records) {
            if (record.timestamp <= min_time)
                min_time = record.timestamp;

            if (record.timestamp >= max_time)
                max_time = record.timestamp;

            int chamber = record.chamber;
            if (chamber == IncubatorState.CHAMBER_UNDEF)
                chamber = IncubatorState.CHAMBER_RIGHT;
            currentTemps.add(new DataPoint(roundTimeFloor(record.timestamp),
                    clearFloat(record.currentTemperature)));
            currentHumids.add(new DataPoint(roundTimeFloor(record.timestamp),
                    clearFloat(record.currentHumidity)));
            neededTemps.add(new DataPoint(roundTimeFloor(record.timestamp),
                    (((int)record.neededTemperature)*10)/10.0f));
            neededHumids.add(new DataPoint(roundTimeFloor(record.timestamp),
                    (int)(record.neededHumidity)));
            heaterStates.add(new DataPoint(roundTimeFloor(record.timestamp),
                    record.heater*HEATER_HEIGHT + HEATER_OFF));
            wetterStates.add(new DataPoint(roundTimeFloor(record.timestamp),
                    record.wetter*WETTER_HEIGHT + WETTER_OFF));
            chamberStates.add(new DataPoint(roundTimeFloor(record.timestamp),
                    CHAMBER_NEUTRAL + chamber*CHAMBER_HEIGHT));
        }

        min_time = roundTimeFloor(min_time);
        max_time = roundTimeCeiling(max_time);

        setTempGraphData(currentTemps, neededTemps, heaterStates, min_time, max_time);
        setHumidGraphData(currentHumids, neededHumids, wetterStates, min_time, max_time);
        setChamberGraphData(chamberStates, min_time, max_time);
    }

    void scanRecords_local(int timespan_type) {
        Log.i(LOG_TAG, "scanRecords_local");
        scanArchiveData(archiver.getLocalArchiveRecords(timespanBegin(timespan_type)));
    }

    void scanRecords_cloud(int timespan_type) {
        try {
            scanArchiveData(archiver.getCloudArchiveRecords(timespanBegin(timespan_type)));
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                        archiver.retrieveCloudArchiveAddress(incubatorAddress);
                } else if (key.compareTo("cloud_archive_mode") == 0) {
                    cloudArchiveMode = sharedPreferences.getBoolean("cloud_archive_mode", true);
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        if (cloudArchiveMode)
            archiver.retrieveCloudArchiveAddress(incubatorAddress);

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
                    hGraph.postDelayed(rGraphUpdate, REQ_TIMEOUT);
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
                hGraph.postDelayed(this, REQ_TIMEOUT);
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
        hGraph.postDelayed(rGraphUpdate, REQ_TIMEOUT);
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