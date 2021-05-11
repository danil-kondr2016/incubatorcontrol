package ru.danilakondratenko.incubatorcontrol;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Environment;
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
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;

public class ArchiveActivity extends AppCompatActivity {
    private static final int RECORD_SIZE = 16;

    /* Timespan indexes */

    private static final int TIMESPAN_LAST_HOUR = 0;
    private static final int TIMESPAN_LAST_DAY = 1;
    private static final int TIMESPAN_LAST_WEEK = 2;
    private static final int TIMESPAN_LAST_MONTH = 3;
    private static final int TIMESPAN_LAST_YEAR = 4;

    /* Timespan length */

    public static final long HOUR  = 3600000L;
    public static final long DAY   = HOUR*24L;
    public static final long WEEK  = DAY*7L;
    public static final long MONTH = DAY*30L;
    public static final long YEAR  = DAY*365L;

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

    public static final int ST_CHAMBER_SHIFT = 3;

    /* Archive error masks */

    public static final byte ER_ZERO          = 0b00000000;

    public static final byte ER_OVERHEAT      = 0b00000001;
    public static final byte ER_CHAMBER_ERROR = 0b00000100;
    public static final byte ER_NO_INTERNET   = (byte) 0b10000000;

    /* Archive state positions */

    public static final double STATE_BASE = 20;

    public static final double WETTER_OFF  = STATE_BASE;
    public static final double WETTER_ON   = WETTER_OFF + 1;
    public static final double HEATER_OFF  = STATE_BASE + 2;
    public static final double HEATER_ON   = HEATER_OFF + 1;

    public static final double CHAMBER_BASE    = STATE_BASE + 5;
    public static final double CHAMBER_LEFT    = CHAMBER_BASE - 1;
    public static final double CHAMBER_NEUTRAL = CHAMBER_BASE;
    public static final double CHAMBER_RIGHT   = CHAMBER_BASE + 1;

    /* GraphView paint parameters */

    public static final int PAINT_STROKE_WIDTH = 5;
    public static final int PAINT_DASH_LENGTH = 10;

    private static final String LOG_TAG = "Archive";

    Spinner spTimespan;

    GraphView graphView;
    LineGraphSeries<DataPoint> temperatureSeries;
    LineGraphSeries<DataPoint> neededTempSeries;
    LineGraphSeries<DataPoint> humiditySeries;
    LineGraphSeries<DataPoint> neededHumidSeries;
    LineGraphSeries<DataPoint> heaterSeries;
    LineGraphSeries<DataPoint> wetterSeries;
    LineGraphSeries<DataPoint> chamberSeries;

    void scanRecords() {
        spTimespan.setSelection(TIMESPAN_LAST_HOUR);
        scanRecords(TIMESPAN_LAST_HOUR);
    }
    
    void scanRecords(int timespan_type) {
        try {
            Log.i(LOG_TAG, String.valueOf(spTimespan.getSelectedItemPosition()));
            File archive = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "archive.dat"
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
            double min_data = Double.MAX_VALUE, max_data = Double.MIN_VALUE;

            double current_temp = 0, old_current_temp = 0;
            double current_humid = 0, old_current_humid = 0;
            double needed_temp = 0, old_needed_temp = 0;
            double needed_humid = 0, old_needed_humid = 0;
            int chamber = 0, old_chamber = 0;
            boolean heater = false, old_heater = false;
            boolean wetter = false;
            
            long timespan_begin = 0;
            Calendar calendar = Calendar.getInstance();
            
            switch (timespan_type) {
                case TIMESPAN_LAST_HOUR:
                    timespan_begin = calendar.getTimeInMillis() - HOUR;
                    break;
                case TIMESPAN_LAST_DAY:
                    timespan_begin = calendar.getTimeInMillis() - DAY;
                    break;
                case TIMESPAN_LAST_WEEK:
                    timespan_begin = calendar.getTimeInMillis() - WEEK;
                    break;
                case TIMESPAN_LAST_MONTH:
                    timespan_begin = calendar.getTimeInMillis() - MONTH;
                    break;
                case TIMESPAN_LAST_YEAR:
                    timespan_begin = calendar.getTimeInMillis() - YEAR;
                    break;
            }
            Log.i(LOG_TAG, String.valueOf(calendar.getTimeInMillis() - timespan_begin));
            while (istream.read(buf) != -1) {
                long time = ByteBuffer.wrap(buf, TIMESTAMP, TIMESTAMP_LEN).getLong();

                if (time < timespan_begin)
                    continue;

                if (time <= min_time)
                    min_time = time;

                if (time >= max_time)
                    max_time = time;

                boolean has_internet = ((buf[ER] & ER_NO_INTERNET) == ER_NO_INTERNET);
                if (has_internet) {
                    old_current_temp = current_temp;
                    old_current_humid = current_humid;
                    old_needed_temp = needed_temp;
                    old_needed_humid = needed_humid;
                    old_chamber = chamber;
                    old_heater = heater;
                }

                current_temp = ByteBuffer.wrap(buf, CUR_TEMP, CUR_TEMP_LEN).getShort() / 256.0;
                current_humid = ByteBuffer.wrap(buf, CUR_HUMID, CUR_HUMID_LEN).getShort() / 256.0;
                needed_temp = ((double)buf[NEEDED_TEMP] + 360) / 10.0;
                needed_humid = buf[NEEDED_HUMID];

                heater = (buf[ST] & ST_HEATER) == ST_HEATER;
                wetter = (buf[ST] & ST_WETTER) == ST_WETTER;

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
                        chamber = old_chamber;
                        break;
                    default:
                        chamber = IncubatorState.CHAMBER_ERROR;
                }

                if (has_internet) {
                    if (current_temp <= min_data)
                        min_data = current_temp;
                    if (current_humid <= min_data)
                        min_data = current_temp;
                    if (needed_temp <= min_data)
                        min_data = needed_temp;
                    if (needed_humid <= min_data)
                        min_data = needed_humid;

                    if (current_temp >= max_data)
                        max_data = current_temp;
                    if (current_humid >= max_data)
                        max_data = current_temp;
                    if (needed_temp >= max_data)
                        max_data = needed_temp;
                    if (needed_humid >= max_data)
                        max_data = needed_humid;
                }

                if (has_internet) {
                    currentTemps.add(new DataPoint(time, current_temp));
                    currentHumids.add(new DataPoint(time, current_humid));

                    neededTemps.add(new DataPoint(time, needed_temp));
                    neededHumids.add(new DataPoint(time, needed_humid));

                    heaterStates.add(new DataPoint(time, (heater) ? HEATER_ON : HEATER_OFF));
                    wetterStates.add(new DataPoint(time, (wetter) ? WETTER_ON : WETTER_OFF));
                    chamberStates.add(new DataPoint(time, CHAMBER_BASE - chamber));
                } else {
                    currentTemps.add(new DataPoint(time, old_current_temp));
                    currentHumids.add(new DataPoint(time, old_current_humid));

                    neededTemps.add(new DataPoint(time, old_needed_temp));
                    neededHumids.add(new DataPoint(time, old_needed_humid));

                    heaterStates.add(new DataPoint(time, (old_heater) ? HEATER_ON : HEATER_OFF));
                    wetterStates.add(new DataPoint(time, WETTER_OFF));
                    chamberStates.add(new DataPoint(time, CHAMBER_BASE - old_chamber));
                }
            }

            graphView.getViewport().setXAxisBoundsManual(true);
            graphView.getViewport().setMinX(min_time);
            graphView.getViewport().setMaxX(max_time);

            graphView.getViewport().setYAxisBoundsManual(true);
            graphView.getViewport().setMinY(min_data - 1);
            graphView.getViewport().setMaxY(max_data + 1);

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

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive);

        ArrayAdapter<CharSequence> adapter =
                ArrayAdapter.createFromResource(
                        this, R.array.timespan_list, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spTimespan = findViewById(R.id.timespan);
        spTimespan.setAdapter(adapter);
        spTimespan.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i(LOG_TAG, String.valueOf(position));
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

        graphView = findViewById(R.id.graph);

        graphView.getViewport().setScalable(true);

        temperatureSeries = new LineGraphSeries<>();
        temperatureSeries.setColor(Color.BLUE);
        temperatureSeries.setTitle(getString(R.string.temperature));
        graphView.addSeries(temperatureSeries);

        neededTempSeries = new LineGraphSeries<>();
        neededTempSeries.setCustomPaint(ntPaint);
        graphView.addSeries(neededTempSeries);

        humiditySeries = new LineGraphSeries<>();
        humiditySeries.setColor(Color.GREEN);
        humiditySeries.setTitle(getString(R.string.humidity));
        graphView.addSeries(humiditySeries);

        neededHumidSeries = new LineGraphSeries<>();
        neededHumidSeries.setCustomPaint(nhPaint);
        graphView.addSeries(neededHumidSeries);

        heaterSeries = new LineGraphSeries<>();
        heaterSeries.setColor(Color.RED);
        heaterSeries.setTitle(getString(R.string.heater_state));
        graphView.addSeries(heaterSeries);

        wetterSeries = new LineGraphSeries<>();
        wetterSeries.setColor(Color.CYAN);
        wetterSeries.setTitle(getString(R.string.wetter_state));
        graphView.addSeries(wetterSeries);

        chamberSeries = new LineGraphSeries<>();
        chamberSeries.setColor(Color.LTGRAY);
        chamberSeries.setTitle(getString(R.string.chamber_pos));
        graphView.addSeries(chamberSeries);

        graphView.getGridLabelRenderer().setHumanRounding(false);
        graphView.getGridLabelRenderer().setLabelFormatter(new DateAsXAxisLabelFormatter(this));
        graphView.getGridLabelRenderer().setNumHorizontalLabels(3);
        graphView.getGridLabelRenderer().setHumanRounding(false);

        graphView.getLegendRenderer().setVisible(true);
        graphView.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
    }

    @Override
    protected void onStart() {
        super.onStart();

        scanRecords();
    }

    public void onUpdateBtnClick(View view) {
        scanRecords(spTimespan.getSelectedItemPosition());
    }
}