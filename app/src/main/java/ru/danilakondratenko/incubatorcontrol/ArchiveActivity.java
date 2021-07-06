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
    private static final int RECORD_SIZE = 16;

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

    /* Archive record indexes */

    public static final int TIMESTAMP          =  0;
    public static final int CUR_TEMP           =  8;
    public static final int CUR_HUMID          = 10;
    public static final int ST                 = 12;
    public static final int NEEDED_TEMP        = 13;
    public static final int NEEDED_HUMID       = 14;
    public static final int ER                 = 15;

    /* Archive record data length */

    public static final int TIMESTAMP_LEN      = 8;
    public static final int CUR_TEMP_LEN       = 2;
    public static final int CUR_HUMID_LEN      = 2;

    /* Archive state masks */

    public static final byte ST_ZERO    = 0b00000000;

    public static final byte ST_HEATER  = 0b00000001;
    public static final byte ST_WETTER  = 0b00000010;
    public static final byte ST_COOLER  = 0b00000100;
    public static final byte ST_CHAMBER = 0b00111000;

    public static final byte ST_CHAMBER_LEFT    = 0b00111000;
    public static final byte ST_CHAMBER_NEUTRAL = 0b00000000;
    public static final byte ST_CHAMBER_RIGHT   = 0b00001000;
    public static final byte ST_CHAMBER_ERROR   = 0b00010000;
    public static final byte ST_CHAMBER_UNDEF   = 0b00011000;

    /* Archive error masks */

    public static final byte ER_ZERO          = 0b00000000;

    public static final byte ER_OVERHEAT      = 0b00000001;
    public static final byte ER_CHAMBER_ERROR = 0b00000100;
    public static final byte ER_NO_INTERNET   = (byte) 0b10000000;

    /* Archive state position */

    public static final double WETTER_OFF  = 0;
    public static final double WETTER_ON   = WETTER_OFF + 1;
    public static final double HEATER_OFF  = 0;
    public static final double HEATER_ON   = HEATER_OFF + 1;

    public static final double CHAMBER_NEUTRAL = 0;
    public static final double CHAMBER_LEFT    = CHAMBER_NEUTRAL - 1;
    public static final double CHAMBER_RIGHT   = CHAMBER_NEUTRAL + 1;

    /* GraphView paint parameters */

    public static final int PAINT_STROKE_WIDTH = 5;
    public static final int PAINT_DASH_LENGTH = 40;

    /* GraphView label parameters */

    public static final int NUM_VERTICAL = 5;
    public static final int NUM_HORIZONTAL = 3;

    private static final String LOG_TAG = "Archive";

    public static final String DEFAULT_INCUBATOR_ADDRESS = "incubator.local";
    public static final String ARCHIVE_ADDRESS = "185.26.121.126";
    public static final String DEFAULT_ARCHIVE_ADDRESS = "185.26.121.126";

    Spinner spTimespan;

    GraphView gvTempGraph;
    LineGraphSeries<DataPoint> temperatureSeries;
    LineGraphSeries<DataPoint> neededTempSeries;

    GraphView gvHumidGraph;
    LineGraphSeries<DataPoint> humiditySeries;
    LineGraphSeries<DataPoint> neededHumidSeries;

    GraphView gvChamberGraph;
    LineGraphSeries<DataPoint> heaterSeries;
    LineGraphSeries<DataPoint> wetterSeries;
    LineGraphSeries<DataPoint> chamberSeries;

    Handler hGraph;
    Runnable rGraphUpdate;

    SharedPreferences prefs;
    SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

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
                archiveAddress = response.body().trim();
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

    void scanRecords_local(int timespan_type) {
        try {
            File archive = new File(
                    Environment.getExternalStorageDirectory(),
                    IncubatorStateActivity.ARCHIVE_FILE_NAME
            );
            archive.setReadable(true);

            FileInputStream istream = new FileInputStream(archive);
            byte[] buf = new byte[RECORD_SIZE];

            ArrayList<DataPoint> currentTemps = new ArrayList<>();
            ArrayList<DataPoint> neededTemps = new ArrayList<>();
            ArrayList<DataPoint> currentHumids = new ArrayList<>();
            ArrayList<DataPoint> neededHumids = new ArrayList<>();
            ArrayList<DataPoint> heaterStates = new ArrayList<>();
            ArrayList<DataPoint> wetterStates = new ArrayList<>();
            ArrayList<DataPoint> chamberStates = new ArrayList<>();

            long min_time = Long.MAX_VALUE, max_time = Long.MIN_VALUE;
            long time = 0;

            double current_temp = 0;
            double current_humid = 0;
            double needed_temp = 0;
            double needed_humid = 0;
            int chamber = 0, old_chamber = 0;
            boolean heater = false;
            boolean wetter = false;

            long timespan_begin = timespanBegin(timespan_type);

            while (istream.read(buf) != -1) {
                time = ByteBuffer.wrap(buf, TIMESTAMP, TIMESTAMP_LEN).getLong();

                if (time < timespan_begin)
                    continue;

                if (time <= min_time)
                    min_time = time;

                if (time >= max_time)
                    max_time = time;


                boolean has_internet = ((buf[ER] & ER_NO_INTERNET) == ER_NO_INTERNET);

                current_temp = ByteBuffer.wrap(buf, CUR_TEMP, CUR_TEMP_LEN).getShort() / 256.0;
                current_humid = ByteBuffer.wrap(buf, CUR_HUMID, CUR_HUMID_LEN).getShort() / 256.0;
                needed_temp = ((double) buf[NEEDED_TEMP] + 360) / 10.0;
                needed_humid = buf[NEEDED_HUMID];

                heater = (buf[ST] & ST_HEATER) == ST_HEATER;
                wetter = (buf[ST] & ST_WETTER) == ST_WETTER;

                old_chamber = chamber;

                switch (buf[ST] & ST_CHAMBER) {
                    case ST_CHAMBER_LEFT:
                        chamber = IncubatorState.CHAMBER_LEFT;
                        break;
                    case ST_CHAMBER_NEUTRAL:
                        chamber = IncubatorState.CHAMBER_NEUTRAL;
                        break;
                    case ST_CHAMBER_RIGHT:
                        chamber = IncubatorState.CHAMBER_RIGHT;
                        break;
                    case ST_CHAMBER_ERROR:
                        chamber = IncubatorState.CHAMBER_ERROR;
                        break;
                    case ST_CHAMBER_UNDEF:
                        chamber = IncubatorState.CHAMBER_RIGHT;
                        break;
                    default:
                        chamber = IncubatorState.CHAMBER_ERROR;
                }

                currentTemps.add(new DataPoint(time, current_temp));
                currentHumids.add(new DataPoint(time, current_humid));

                neededTemps.add(new DataPoint(time, needed_temp));
                neededHumids.add(new DataPoint(time, needed_humid));

                heaterStates.add(new DataPoint(time, (heater) ? HEATER_ON : HEATER_OFF));
                wetterStates.add(new DataPoint(time, (wetter) ? WETTER_ON : WETTER_OFF));
                chamberStates.add(new DataPoint(time, CHAMBER_NEUTRAL - chamber));
            }

            DataPoint[] c_temps = new DataPoint[currentTemps.size()];
            DataPoint[] n_temps = new DataPoint[neededTemps.size()];
            DataPoint[] c_humids = new DataPoint[currentHumids.size()];
            DataPoint[] n_humids = new DataPoint[neededHumids.size()];
            DataPoint[] heaters = new DataPoint[heaterStates.size()];
            DataPoint[] wetters = new DataPoint[wetterStates.size()];
            DataPoint[] chambers = new DataPoint[chamberStates.size()];

            currentTemps.toArray(c_temps);
            neededTemps.toArray(n_temps);
            currentHumids.toArray(c_humids);
            neededHumids.toArray(n_humids);
            heaterStates.toArray(heaters);
            wetterStates.toArray(wetters);
            chamberStates.toArray(chambers);

            temperatureSeries.resetData(c_temps);
            neededTempSeries.resetData(n_temps);
            humiditySeries.resetData(c_humids);
            neededHumidSeries.resetData(n_humids);
            heaterSeries.resetData(heaters);
            wetterSeries.resetData(wetters);
            chamberSeries.resetData(chambers);

            gvTempGraph.getViewport().setXAxisBoundsManual(true);
            gvTempGraph.getViewport().setMinX(min_time);
            gvTempGraph.getViewport().setMaxX(max_time);
            gvTempGraph.getGridLabelRenderer().setNumHorizontalLabels(NUM_HORIZONTAL);
            gvTempGraph.getGridLabelRenderer().setNumVerticalLabels(NUM_VERTICAL);

            gvHumidGraph.getViewport().setXAxisBoundsManual(true);
            gvHumidGraph.getViewport().setMinX(min_time);
            gvHumidGraph.getViewport().setMaxX(max_time);
            gvHumidGraph.getGridLabelRenderer().setNumHorizontalLabels(NUM_HORIZONTAL);
            gvHumidGraph.getGridLabelRenderer().setNumVerticalLabels(NUM_VERTICAL);

            gvChamberGraph.getViewport().setXAxisBoundsManual(true);
            gvChamberGraph.getViewport().setMinX(min_time);
            gvChamberGraph.getViewport().setMaxX(max_time);
            gvChamberGraph.getGridLabelRenderer().setNumHorizontalLabels(NUM_HORIZONTAL);
            gvChamberGraph.getGridLabelRenderer().setNumVerticalLabels(NUM_VERTICAL);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    void scanRecords_cloud(int timespan_type) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://" + ARCHIVE_ADDRESS)
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
                            record.heater + HEATER_OFF));
                    wetterStates.add(new DataPoint(record.timestamp,
                            record.wetter + WETTER_OFF));
                    chamberStates.add(new DataPoint(record.timestamp, CHAMBER_NEUTRAL + chamber));
                }

                DataPoint[] c_temps = new DataPoint[currentTemps.size()];
                DataPoint[] n_temps = new DataPoint[neededTemps.size()];
                DataPoint[] c_humids = new DataPoint[currentHumids.size()];
                DataPoint[] n_humids = new DataPoint[neededHumids.size()];
                DataPoint[] heaters = new DataPoint[heaterStates.size()];
                DataPoint[] wetters = new DataPoint[wetterStates.size()];
                DataPoint[] chambers = new DataPoint[chamberStates.size()];

                currentTemps.toArray(c_temps);
                neededTemps.toArray(n_temps);
                currentHumids.toArray(c_humids);
                neededHumids.toArray(n_humids);
                heaterStates.toArray(heaters);
                wetterStates.toArray(wetters);
                chamberStates.toArray(chambers);

                temperatureSeries.resetData(c_temps);
                neededTempSeries.resetData(n_temps);
                humiditySeries.resetData(c_humids);
                neededHumidSeries.resetData(n_humids);
                heaterSeries.resetData(heaters);
                wetterSeries.resetData(wetters);
                chamberSeries.resetData(chambers);

                gvTempGraph.getViewport().setXAxisBoundsManual(true);
                gvTempGraph.getViewport().setMinX(min_time);
                gvTempGraph.getViewport().setMaxX(max_time);
                gvTempGraph.getGridLabelRenderer().setNumHorizontalLabels(NUM_HORIZONTAL);
                gvTempGraph.getGridLabelRenderer().setNumVerticalLabels(NUM_VERTICAL);

                gvHumidGraph.getViewport().setXAxisBoundsManual(true);
                gvHumidGraph.getViewport().setMinX(min_time);
                gvHumidGraph.getViewport().setMaxX(max_time);
                gvHumidGraph.getGridLabelRenderer().setNumHorizontalLabels(NUM_HORIZONTAL);
                gvHumidGraph.getGridLabelRenderer().setNumVerticalLabels(NUM_VERTICAL);

                gvChamberGraph.getViewport().setXAxisBoundsManual(true);
                gvChamberGraph.getViewport().setMinX(min_time);
                gvChamberGraph.getViewport().setMaxX(max_time);
                gvChamberGraph.getGridLabelRenderer().setNumHorizontalLabels(NUM_HORIZONTAL);
                gvChamberGraph.getGridLabelRenderer().setNumVerticalLabels(NUM_VERTICAL);
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
                    requestArchiveAddress();
                } else if (key.compareTo("cloud_archive_mode") == 0) {
                    cloudArchiveMode = sharedPreferences.getBoolean("cloud_archive_mode", true);
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

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
                if (position != TIMESPAN_CURRENT) {
                    hGraph.removeCallbacks(rGraphUpdate);

                } else {
                    hGraph.postDelayed(rGraphUpdate, IncubatorStateActivity.REQ_TIMEOUT);
                }
                scanRecords(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        Paint ntPaint = new Paint();
        ntPaint.setColor(Color.BLUE);
        ntPaint.setStyle(Paint.Style.STROKE);
        ntPaint.setStrokeWidth(PAINT_STROKE_WIDTH);
        ntPaint.setPathEffect(new DashPathEffect(new float[]{PAINT_DASH_LENGTH, PAINT_DASH_LENGTH}, 0));

        Paint nhPaint = new Paint();
        nhPaint.setColor(Color.GREEN);
        nhPaint.setStyle(Paint.Style.STROKE);
        nhPaint.setStrokeWidth(PAINT_STROKE_WIDTH);
        nhPaint.setPathEffect(new DashPathEffect(new float[]{PAINT_DASH_LENGTH, PAINT_DASH_LENGTH}, 0));

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

        temperatureSeries = new LineGraphSeries<>();
        temperatureSeries.setColor(Color.BLUE);
        temperatureSeries.setTitle(getString(R.string.temperature));
        gvTempGraph.addSeries(temperatureSeries);

        neededTempSeries = new LineGraphSeries<>();
        neededTempSeries.setCustomPaint(ntPaint);
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

        humiditySeries = new LineGraphSeries<>();
        humiditySeries.setColor(Color.GREEN);
        humiditySeries.setTitle(getString(R.string.humidity));
        gvHumidGraph.addSeries(humiditySeries);

        neededHumidSeries = new LineGraphSeries<>();
        neededHumidSeries.setCustomPaint(nhPaint);
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