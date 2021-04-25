package ru.danilakondratenko.incubatorcontrol;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.VectorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toolbar;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class IncubatorStateActivity extends AppCompatActivity {
    public static final int REQ_TIMEOUT = 2000;
    public static final String DEFAULT_INCUBATOR_ADDRESS = "incubator.local";

    public static final int HAS_INTERNET = 0x11;
    public static final int NO_INTERNET = 0x12;

    public static final int TEMP_ERROR = 0x13;
    public static final int CHANGE_PHASE = 0x14;
    public static final int NO_ERROR = 0x15;

    public static final int NEED_CONFIG = 0x16;

    public static final int ALARM_HEATER = 0x20;

    static final float BODY_WIDTH = 605, BODY_HEIGHT = 870;
    static final float COOLER_X = 260, COOLER_Y = 765;
    static final float WETTER_X = 30, WETTER_Y = 264;
    static final float HEATER_X = 237, HEATER_Y = 704;
    static final float CHAMBER_X = 92, CHAMBER_Y = 297;
    static final float SCREEN_X = 256, SCREEN_Y = 32;
    static final float MINUS_BTN_X = 376, MENU_BTN_X = 496, PLUS_BTN_X = 256, X_BTN_Y = 120;
    static final float SCREEN_FONT_SIZE = 22;

    static final int CURRENT_STATE_MODE = 0;
    static final int NEEDED_TEMPERATURE_MODE  = 1;
    static final int NEEDED_HUMIDITY_MODE = 2;
    static final int ROTATIONS_PER_DAY_MODE = 3;
    static final int CURRENT_PROGRAM_MODE = 4;

    static final int DELTA_MODE = 1;
    static final int N_MODES = 5;

    static final float EPSILON = 0.005f;
    static final float MIN_TEMPERATURE = 36f;
    static final float MAX_TEMPERATURE = 38f;
    static final float DELTA_TEMPERATURE = 0.1f;

    static final float MIN_HUMIDITY = 40f;
    static final float MAX_HUMIDITY = 80f;
    static final float DELTA_HUMIDITY = 1f;

    static final int MIN_ROT_PER_DAY = 1;
    static final int MAX_ROT_PER_DAY = 24;
    static final int DELTA_ROT_PER_DAY = 1;

    static final long ROTATION_ANIMATION_DURATION = 3000;
    static final int ROTATION_ANIMATION_POS_LEFT = -45;
    static final int ROTATION_ANIMATION_POS_NEUTRAL = 0;
    static final int ROTATION_ANIMATION_POS_RIGHT = 45;

    static final int SCREEN_OFF = 0;
    static final int SCREEN_ON = 1;

    static final long COOLER_ROTATION_DURATION = 100;
    static final int DELTA_PHASE = 1;
    static final int N_PHASES = 3;

    static final long ALARM_BLINKING_DURATION = 500;

    String INCUBATOR_ADDRESS = DEFAULT_INCUBATOR_ADDRESS;
    int NUMBER_OF_PROGRAMS;
    static final int DELTA_CURRENT_PROGRAM = 1;

    static final String TEMPERATURE_FORMAT = "%.4g °C";
    static final String HUMIDITY_FORMAT = "%.2g%%";

    static final String CURRENT_STATE_FORMAT =
            " Температура " + TEMPERATURE_FORMAT + "\n"
          + " Влажность   " + HUMIDITY_FORMAT;

    static final String NEEDED_TEMPERATURE_FORMAT =
            " Нужная температура\n " + TEMPERATURE_FORMAT;

    static final String NEEDED_HUMIDITY_FORMAT =
            " Нужная влажность\n " + HUMIDITY_FORMAT;

    static final String ROTATIONS_PER_DAY_FORMAT =
            " Кол-во поворотов\n яиц в день : %d";

    static final String CURRENT_PROGRAM_FORMAT =
            " Режим\n P%d";

    private static final String LOG_TAG = "Incubator";

    float k;

    ImageView ivIncubatorBody,
            ivIncubatorCooler, ivIncubatorWetter, ivIncubatorHeater, ivIncubatorChamber,
            ivIncubatorScreen, ivIncubatorMinusBtn, ivIncubatorMenuBtn, ivIncubatorPlusBtn;

    TextView tvIncubatorScreen;
    TextView tvIncubatorUptime;

    Toolbar toolbar;

    int rotatePhase = 0;
    int heaterAlarmPhase = 0;

    Timer reqTimer, rotTimer, alarmTimer;
    private int mode = CURRENT_STATE_MODE;

    private IncubatorState state;
    private IncubatorConfig cfg;
    private int oldChamber = IncubatorState.CHAMBER_NEUTRAL;

    Handler hInternet, hTempError, hCooler, hAlarm, hConfig;
    private boolean hasInternet, needConfig;

    SharedPreferences prefs;

    private boolean makeRequest(String req) {
        try {
            String str = new NetworkRequestTask().execute(
                    "http://" + INCUBATOR_ADDRESS + "/control",
                    "POST", "text/plain", req
            ).get(REQ_TIMEOUT, TimeUnit.MILLISECONDS);
            if (str == null) {
                hInternet.sendEmptyMessage(NO_INTERNET);
                return false;
            } else {
                hInternet.sendEmptyMessage(HAS_INTERNET);
            }

            String[] lines = str.split("\r\n");
            boolean has_error = false;
            for (int i = 0; i < lines.length; i++) {
                lines[i] = lines[i].replace("\r\n", "").trim();
                String[] args = lines[i].split(" ");
                if (args[0].compareTo("current_temp") == 0) {
                    state.currentTemperature = Float.parseFloat(args[1]);
                } else if (args[0].compareTo("current_humid") == 0) {
                    state.currentHumidity = Float.parseFloat(args[1]);
                } else if (args[0].compareTo("needed_temp") == 0) {
                    cfg.neededTemperature = Float.parseFloat(args[1]);
                } else if (args[0].compareTo("needed_humid") == 0) {
                    cfg.neededHumidity = Float.parseFloat(args[1]);
                } else if (args[0].compareTo("rotations_per_day") == 0) {
                    cfg.rotationsPerDay = Integer.parseInt(args[1]);
                } else if (args[0].compareTo("heater") == 0) {
                    state.heater = Integer.parseInt(args[1]) > 0;
                } else if (args[0].compareTo("cooler") == 0) {
                    state.cooler = Integer.parseInt(args[1]) > 0;
                } else if (args[0].compareTo("wetter") == 0) {
                    state.wetter = Integer.parseInt(args[1]) > 0;
                } else if (args[0].compareTo("chamber") == 0) {
                    oldChamber = state.chamber;
                    state.chamber = Integer.parseInt(args[1]);
                } else if (args[0].compareTo("overheat") == 0) {
                    hTempError.sendEmptyMessage(TEMP_ERROR);
                    has_error = true;
                } else if (args[0].compareTo("uptime") == 0) {
                    state.uptime = Long.parseLong(args[1]);
                } else if (args[0].compareTo("number_of_programs") == 0) {
                    NUMBER_OF_PROGRAMS = Integer.parseInt(args[1]);
                } else if (args[0].compareTo("current_program") == 0) {
                    cfg.currentProgram = Integer.parseInt(args[1]);
                } else if (args[0].compareTo("changed") == 0 ) {
                    hConfig.sendEmptyMessage(NEED_CONFIG);
                }
            }
            if (!has_error) {
                hTempError.sendEmptyMessage(NO_ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    void updateCooler() {
        if (state.cooler) {
            switch (rotatePhase) {
                case 0:
                    ivIncubatorCooler.setImageResource(R.drawable.ic_incubator_cooler1);
                    break;
                case 1:
                    ivIncubatorCooler.setImageResource(R.drawable.ic_incubator_cooler2);
                    break;
                case 2:
                    ivIncubatorCooler.setImageResource(R.drawable.ic_incubator_cooler3);
                    break;
            }
        } else {
            ivIncubatorCooler.setImageResource(R.drawable.ic_incubator_cooler0);
        }
    }

    void updateWetter() {
        if (state.wetter) {
            ivIncubatorWetter.setImageResource(R.drawable.ic_incubator_wetter1);
        } else {
            ivIncubatorWetter.setImageResource(R.drawable.ic_incubator_wetter0);
        }
    }

    void updateHeater() {
        if (state.overheat) {
            if (heaterAlarmPhase == 0) {
                ivIncubatorHeater.setImageResource(R.drawable.ic_incubator_heater_error);
                heaterAlarmPhase = 1;
            } else {
                ivIncubatorHeater.setImageResource(R.drawable.ic_incubator_heater0);
                heaterAlarmPhase = 0;
            }
        } else if (state.heater) {
            ivIncubatorHeater.setImageResource(R.drawable.ic_incubator_heater1);
        } else {
            ivIncubatorHeater.setImageResource(R.drawable.ic_incubator_heater0);
        }
    }

    void updateScreen(int n_view) {
        switch (n_view) {
            case 0:
                ivIncubatorScreen.setImageResource(R.drawable.ic_incubator_screen0);
                break;
            case 1:
                ivIncubatorScreen.setImageResource(R.drawable.ic_incubator_screen1);
                break;
        }
    }

    void updateChamber() {
        if (oldChamber == state.chamber)
            return;

        if (state.chamber == IncubatorState.CHAMBER_ERROR)
            ivIncubatorChamber.setImageResource(R.drawable.ic_incubator_chamber_error);
        else
            ivIncubatorChamber.setImageResource(R.drawable.ic_incubator_chamber);

        switch (state.chamber) {
            case IncubatorState.CHAMBER_LEFT:
                ivIncubatorChamber.animate()
                        .setDuration(ROTATION_ANIMATION_DURATION)
                        .rotation(ROTATION_ANIMATION_POS_LEFT)
                        .start();
                break;
            case IncubatorState.CHAMBER_NEUTRAL:
                ivIncubatorChamber.animate()
                        .setDuration(ROTATION_ANIMATION_DURATION)
                        .rotation(ROTATION_ANIMATION_POS_NEUTRAL)
                        .start();
                break;
            case IncubatorState.CHAMBER_RIGHT:
                ivIncubatorChamber.animate()
                        .setDuration(ROTATION_ANIMATION_DURATION)
                        .rotation(ROTATION_ANIMATION_POS_RIGHT)
                        .start();
                break;
            case IncubatorState.CHAMBER_ERROR:
                ivIncubatorChamber.setRotation(ROTATION_ANIMATION_POS_NEUTRAL);
                break;
        }
    }
    
    void updateIncubator() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateCooler();
                if (!state.overheat)
                    updateHeater();
                updateWetter();
                updateChamber();

                if ((state.uptime > 0) && (hasInternet)) {
                    long n_day = state.uptime / 86400;
                    tvIncubatorUptime.setText(
                            String.format(Locale.getDefault(),
                                    "%d-й день инкубации",
                                    n_day + 1)
                    );
                } else {
                    tvIncubatorUptime.setText("");
                }

                switch (mode) {
                    case CURRENT_STATE_MODE:
                        tvIncubatorScreen.setText(
                                String.format(Locale.getDefault(),
                                        CURRENT_STATE_FORMAT,
                                        state.currentTemperature, state.currentHumidity)
                        );
                        break;
                    case NEEDED_TEMPERATURE_MODE:
                        tvIncubatorScreen.setText(
                                String.format(Locale.getDefault(),
                                        NEEDED_TEMPERATURE_FORMAT,
                                        cfg.neededTemperature)
                        );
                        break;
                    case NEEDED_HUMIDITY_MODE:
                        tvIncubatorScreen.setText(
                                String.format(Locale.getDefault(),
                                        NEEDED_HUMIDITY_FORMAT,
                                        cfg.neededHumidity)
                        );
                        break;
                    case ROTATIONS_PER_DAY_MODE:
                        tvIncubatorScreen.setText(
                                String.format(Locale.getDefault(),
                                        ROTATIONS_PER_DAY_FORMAT,
                                        cfg.rotationsPerDay)
                        );
                        break;
                    case CURRENT_PROGRAM_MODE:
                        tvIncubatorScreen.setText(
                                String.format(Locale.getDefault(),
                                        CURRENT_PROGRAM_FORMAT,
                                        cfg.currentProgram)
                        );
                        break;
                }
            }
        });
    }

    boolean requestState() {
        return makeRequest("request_state\r\n");
    }

    boolean requestConfig() {
        return makeRequest("request_config\r\n");
    }

    boolean sendConfig() {
        boolean reqState = true;
        reqState = reqState && makeRequest(
                String.format(Locale.US,
                        "needed_temp %.2f\r\n",
                        cfg.neededTemperature
                )
        );
        reqState = reqState && makeRequest(
                String.format(Locale.US,
                        "needed_humid %.2f\r\n",
                        cfg.neededHumidity
                )
        );
        reqState = reqState && makeRequest(
                String.format(Locale.US,
                        "rotations_per_day %d\r\n",
                        cfg.rotationsPerDay
                )
        );
        reqState = reqState && makeRequest(
                String.format(Locale.US,
                        "switch_to_program %d\r\n",
                        cfg.currentProgram
                )
        );
        return reqState;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        INCUBATOR_ADDRESS = prefs.getString("incubator_address", DEFAULT_INCUBATOR_ADDRESS);
        prefs.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                Log.i(LOG_TAG, key);
                if (key.compareTo("incubator_address") == 0) {
                    INCUBATOR_ADDRESS = sharedPreferences.getString(
                            "incubator_address", DEFAULT_INCUBATOR_ADDRESS
                    );
                    requestConfig();
                    requestState();
                }
            }
        });

        state = new IncubatorState();
        cfg = new IncubatorConfig();

        hInternet = new Handler(Looper.myLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case NO_INTERNET:
                        hasInternet = false;
                        needConfig = true;
                        mode = CURRENT_STATE_MODE;

                        cfg.clear();
                        state.clear();
                        updateIncubator();
                        updateScreen(SCREEN_OFF);
                        tvIncubatorScreen.setText("");

                        break;
                    case HAS_INTERNET:
                        if (needConfig)
                            hConfig.sendEmptyMessage(NEED_CONFIG);
                        hasInternet = true;
                        updateScreen(SCREEN_ON);
                        break;
                }
                return false;
            }
        });

        hConfig = new Handler(Looper.myLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                if (msg.what == NEED_CONFIG) {
                    requestConfig();
                    updateIncubator();
                    needConfig = false;
                }

                return true;
            }
        });

        hTempError = new Handler(Looper.myLooper(), new Handler.Callback() {

            @Override
            public boolean handleMessage(@NonNull Message msg) {
                if (msg.what == TEMP_ERROR) {
                    NotificationCompat.Builder bld =
                            new NotificationCompat.Builder(IncubatorStateActivity.this)
                            .setSmallIcon(android.R.drawable.ic_dialog_alert)
                            .setContentTitle(getString(R.string.temp_error_title))
                            .setContentText(getString(R.string.temp_error));
                    NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        NotificationChannel channel = new NotificationChannel(
                                "IncubatorTempError",
                                "Incubator temp error channel",
                                NotificationManager.IMPORTANCE_HIGH
                        );
                        manager.createNotificationChannel(channel);
                        bld.setChannelId("IncubatorTempError");
                    }

                    Notification notification = bld.build();

                    if (!state.overheat) {
                        manager.notify(1, notification);
                    }

                    state.overheat = true;
                } else if (msg.what == NO_ERROR) {
                    state.overheat = false;
                }
                return true;
            }
        });

        hCooler = new Handler(Looper.myLooper(), new Handler.Callback() {

            @Override
            public boolean handleMessage(@NonNull Message msg) {
                if (msg.what == CHANGE_PHASE) {
                    rotatePhase = (rotatePhase + DELTA_PHASE) % N_PHASES;
                    updateCooler();
                }
                return true;
            }
        });

        hAlarm = new Handler(Looper.myLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                if (msg.what == ALARM_HEATER) {
                    updateHeater();
                }
                return true;
            }
        });

        reqTimer = new Timer("IncubatorStateActivity ReqTimer");

        rotTimer = new Timer("IncubatorStateActivity RotTimer");

        alarmTimer = new Timer("IncubatorStateActivity AlarmTimer");

        rotTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (state.cooler) {
                    hCooler.sendEmptyMessage(CHANGE_PHASE);
                } else {
                    rotatePhase = 0;
                }
            }
        }, 0, COOLER_ROTATION_DURATION);

        alarmTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (state.overheat)
                    hAlarm.sendEmptyMessage(ALARM_HEATER);
            }
        }, 0, ALARM_BLINKING_DURATION);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        toolbar = new Toolbar(this);
        toolbar.inflateMenu(R.menu.incubator_menu);

        Menu menu = toolbar.getMenu();
        MenuItem miConfig = (MenuItem)menu.findItem(R.id.config);
        miConfig.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Log.i(LOG_TAG, "" + item.getItemId());
                if (item.getItemId() == R.id.config) {
                    Intent intent = new Intent(IncubatorStateActivity.this, IncubatorSettingsActivity.class);
                    startActivity(intent);
                }
                return true;
            }
        });
        toolbar.setBackgroundResource(R.color.colorPrimary);
        setActionBar(toolbar);
        addContentView(toolbar, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        VectorDrawable vdIncubatorBody = (VectorDrawable)ContextCompat.getDrawable(this, R.drawable.ic_incubator_body);
        VectorDrawable vdIncubatorCooler = (VectorDrawable)ContextCompat.getDrawable(this, R.drawable.ic_incubator_cooler0);
        VectorDrawable vdIncubatorHeater = (VectorDrawable)ContextCompat.getDrawable(this, R.drawable.ic_incubator_heater0);
        VectorDrawable vdIncubatorWetter = (VectorDrawable)ContextCompat.getDrawable(this, R.drawable.ic_incubator_wetter0);
        VectorDrawable vdIncubatorChamber = (VectorDrawable)ContextCompat.getDrawable(this, R.drawable.ic_incubator_chamber);
        VectorDrawable vdIncubatorScreen = (VectorDrawable)ContextCompat.getDrawable(this, R.drawable.ic_incubator_screen0);
        VectorDrawable vdIncubatorBtn = (VectorDrawable)ContextCompat.getDrawable(this, R.drawable.ic_incubator_minus_btn);

        int screenWidth = getApplicationContext().getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getApplicationContext().getResources().getDisplayMetrics().heightPixels;
        int screenCenterX = screenWidth / 2;
        int screenCenterY = screenHeight / 2;
        int margin = screenCenterX / 16;

        /* Creating incubator GUI views */

        int incubatorBodyDrawableWidth = vdIncubatorBody.getIntrinsicWidth();
        int incubatorBodyDrawableHeight = vdIncubatorBody.getIntrinsicHeight();
        int incubatorBodyWidth = screenWidth - margin * 2;
        k = incubatorBodyWidth / (float)incubatorBodyDrawableWidth;

        int incubatorBodyHeight = (int)(incubatorBodyDrawableHeight * k);

        float incubatorX = screenCenterX - incubatorBodyWidth / 2.0f;
        float incubatorY = screenCenterY - incubatorBodyHeight / 2.0f;

        ivIncubatorBody = new ImageView(this);
        ivIncubatorBody.setImageResource(R.drawable.ic_incubator_body);
        ivIncubatorBody.setX(incubatorX);
        ivIncubatorBody.setY(incubatorY);
        ivIncubatorBody.setVisibility(View.VISIBLE);
        addContentView(ivIncubatorBody, new ViewGroup.LayoutParams(incubatorBodyWidth, incubatorBodyHeight));

        ivIncubatorCooler = new ImageView(this);
        ivIncubatorCooler.setImageResource(R.drawable.ic_incubator_cooler0);
        ivIncubatorCooler.setX(incubatorX + (COOLER_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorCooler.setY(incubatorY + (COOLER_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorCooler.setVisibility(View.VISIBLE);
        addContentView(ivIncubatorCooler, new ViewGroup.LayoutParams(
                (int)(vdIncubatorCooler.getIntrinsicWidth() * k),
                (int)(vdIncubatorCooler.getIntrinsicHeight() * k)
        ));

        ivIncubatorWetter = new ImageView(this);
        ivIncubatorWetter.setImageResource(R.drawable.ic_incubator_wetter0);
        ivIncubatorWetter.setX(incubatorX + (WETTER_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorWetter.setY(incubatorY + (WETTER_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorWetter.setVisibility(View.VISIBLE);
        addContentView(ivIncubatorWetter, new ViewGroup.LayoutParams(
                (int)(vdIncubatorWetter.getIntrinsicWidth() * k),
                (int)(vdIncubatorWetter.getIntrinsicHeight() * k)
        ));

        ivIncubatorHeater = new ImageView(this);
        ivIncubatorHeater.setImageResource(R.drawable.ic_incubator_heater0);
        ivIncubatorHeater.setX(incubatorX + (HEATER_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorHeater.setY(incubatorY + (HEATER_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorHeater.setVisibility(View.VISIBLE);
        addContentView(ivIncubatorHeater, new ViewGroup.LayoutParams(
                (int)(vdIncubatorHeater.getIntrinsicWidth() * k),
                (int)(vdIncubatorHeater.getIntrinsicHeight() * k)
        ));

        ivIncubatorChamber = new ImageView(this);
        ivIncubatorChamber.setImageResource(R.drawable.ic_incubator_chamber);
        ivIncubatorChamber.setX(incubatorX + (CHAMBER_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorChamber.setY(incubatorY + (CHAMBER_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorChamber.setVisibility(View.VISIBLE);
        addContentView(ivIncubatorChamber, new ViewGroup.LayoutParams(
                (int)(vdIncubatorChamber.getIntrinsicWidth() * k),
                (int)(vdIncubatorChamber.getIntrinsicHeight() * k)
        ));

        ivIncubatorScreen = new ImageView(this);
        ivIncubatorScreen.setImageResource(R.drawable.ic_incubator_screen0);
        ivIncubatorScreen.setX(incubatorX + (SCREEN_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorScreen.setY(incubatorY + (SCREEN_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorScreen.setVisibility(View.VISIBLE);
        addContentView(ivIncubatorScreen, new ViewGroup.LayoutParams(
                (int)(vdIncubatorScreen.getIntrinsicWidth() * k),
                (int)(vdIncubatorScreen.getIntrinsicHeight() * k)
        ));

        ivIncubatorMinusBtn = new ImageView(this);
        ivIncubatorMinusBtn.setImageResource(R.drawable.ic_incubator_minus_btn);
        ivIncubatorMinusBtn.setX(incubatorX + (MINUS_BTN_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorMinusBtn.setY(incubatorY + (X_BTN_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorMinusBtn.setVisibility(View.VISIBLE);
        ivIncubatorMinusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasInternet)
                    return;
                switch (mode) {
                    case NEEDED_TEMPERATURE_MODE:
                        if (Math.abs(cfg.neededTemperature - MIN_TEMPERATURE) >= EPSILON) {
                            cfg.neededTemperature -= DELTA_TEMPERATURE;
                            updateIncubator();
                        }
                        break;
                    case NEEDED_HUMIDITY_MODE:
                        if (Math.abs(cfg.neededHumidity - MIN_HUMIDITY) >= EPSILON) {
                            cfg.neededHumidity -= DELTA_HUMIDITY;
                            updateIncubator();
                        }
                        break;
                    case ROTATIONS_PER_DAY_MODE:
                        if ((cfg.rotationsPerDay) >= MIN_ROT_PER_DAY) {
                            cfg.rotationsPerDay -= DELTA_ROT_PER_DAY;
                            updateIncubator();
                        }
                        break;
                    case CURRENT_PROGRAM_MODE:
                        if ((cfg.currentProgram) > 0) {
                            cfg.currentProgram -= DELTA_CURRENT_PROGRAM;
                            updateIncubator();
                        }
                        break;
                }
            }
        });
        addContentView(ivIncubatorMinusBtn, new ViewGroup.LayoutParams(
                (int)(vdIncubatorBtn.getIntrinsicWidth() * k),
                (int)(vdIncubatorBtn.getIntrinsicHeight() * k)
        ));

        ivIncubatorMenuBtn = new ImageView(this);
        ivIncubatorMenuBtn.setImageResource(R.drawable.ic_incubator_menu_btn);
        ivIncubatorMenuBtn.setX(incubatorX + (MENU_BTN_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorMenuBtn.setY(incubatorY + (X_BTN_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorMenuBtn.setVisibility(View.VISIBLE);
        ivIncubatorMenuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasInternet)
                    return;
                mode = (mode + DELTA_MODE) % N_MODES;
                updateIncubator();
                if (mode == CURRENT_STATE_MODE) {
                    sendConfig();
                }
            }
        });
        addContentView(ivIncubatorMenuBtn, new ViewGroup.LayoutParams(
                (int)(vdIncubatorBtn.getIntrinsicWidth() * k),
                (int)(vdIncubatorBtn.getIntrinsicHeight() * k)
        ));

        ivIncubatorPlusBtn = new ImageView(this);
        ivIncubatorPlusBtn.setImageResource(R.drawable.ic_incubator_plus_btn);
        ivIncubatorPlusBtn.setX(incubatorX + (PLUS_BTN_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorPlusBtn.setY(incubatorY + (X_BTN_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorPlusBtn.setVisibility(View.VISIBLE);
        ivIncubatorPlusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                        if (!hasInternet)
                            return;
                        switch (mode) {
                            case NEEDED_TEMPERATURE_MODE:
                                if (Math.abs(MAX_TEMPERATURE - cfg.neededTemperature) >= EPSILON) {
                                    cfg.neededTemperature += DELTA_TEMPERATURE;
                                    updateIncubator();
                                }
                                break;
                            case NEEDED_HUMIDITY_MODE:
                                if (Math.abs(MAX_HUMIDITY - cfg.neededHumidity) >= EPSILON) {
                                    cfg.neededHumidity += DELTA_HUMIDITY;
                                    updateIncubator();
                                }
                                break;
                            case ROTATIONS_PER_DAY_MODE:
                                if ((cfg.rotationsPerDay) <= MAX_ROT_PER_DAY) {
                                    cfg.rotationsPerDay += DELTA_ROT_PER_DAY;
                                    updateIncubator();
                                }
                                break;
                            case CURRENT_PROGRAM_MODE:
                                if ((cfg.currentProgram) < NUMBER_OF_PROGRAMS-1) {
                                    cfg.currentProgram += DELTA_CURRENT_PROGRAM;
                                    updateIncubator();
                                }
                                break;
                        }
                    }
        });
        addContentView(ivIncubatorPlusBtn, new ViewGroup.LayoutParams(
                (int)(vdIncubatorBtn.getIntrinsicWidth() * k),
                (int)(vdIncubatorBtn.getIntrinsicHeight() * k)
        ));

        tvIncubatorScreen = new TextView(this);
        tvIncubatorScreen.setX(incubatorX + (SCREEN_X / BODY_WIDTH) * incubatorBodyWidth + 5);
        tvIncubatorScreen.setY(incubatorY + (SCREEN_Y / BODY_HEIGHT) * incubatorBodyHeight + 3);
        tvIncubatorScreen.setTextColor(Color.WHITE);
        tvIncubatorScreen.setTypeface(Typeface.MONOSPACE);
        tvIncubatorScreen.setTextSize(SCREEN_FONT_SIZE * k);
        addContentView(tvIncubatorScreen, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        tvIncubatorUptime = new TextView(this);
        tvIncubatorUptime.setX(incubatorX);
        tvIncubatorUptime.setY(incubatorY + incubatorBodyHeight);
        tvIncubatorUptime.setTextColor(Color.WHITE);
        tvIncubatorUptime.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvIncubatorUptime.setTextSize(SCREEN_FONT_SIZE * k);
        addContentView(tvIncubatorUptime, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    @Override
    protected void onStart() {
        super.onStart();

        requestConfig();

        reqTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                requestState();

                if (hasInternet) {
                    if (mode == CURRENT_STATE_MODE)
                        updateIncubator();
                }
            }
        }, 0, REQ_TIMEOUT);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
