package ru.danilakondratenko.incubatorcontrol;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class IncubatorStateActivity extends AppCompatActivity {
    /* Incubator request constants */
    public static final int REQ_TIMEOUT = 2000;
    public static final String DEFAULT_INCUBATOR_ADDRESS = "incubator.local";

    /* Handler message codes */
    public static final int INCUBATOR_ACCESSIBLE = 0x11;
    public static final int INCUBATOR_INACCESSIBLE = 0x12;
    public static final int INCUBATOR_TURNED_OFF = 0x13;

    public static final int OVERHEAT_ERROR = 0x14;
    public static final int CHANGE_PHASE = 0x15;
    public static final int NO_ERROR = 0x16;

    public static final int CONFIG_AVAILABLE = 0x17;

    public static final int ALARM_HEATER = 0x20;

    /* Values needed to calculate coordinates */
    static final float BODY_WIDTH = 605, BODY_HEIGHT = 870;
    static final float COOLER_X = 260, COOLER_Y = 765;
    static final float WETTER_X = 30, WETTER_Y = 264;
    static final float HEATER_X = 237, HEATER_Y = 704;
    static final float CHAMBER_X = 92, CHAMBER_Y = 297;
    static final float SCREEN_X = 256, SCREEN_Y = 32;
    static final float MINUS_BTN_X = 376, MENU_BTN_X = 496, PLUS_BTN_X = 256, X_BTN_Y = 120;
    static final float ARCHIVE_BTN_X = 130, ARCHIVE_BTN_Y = 55;

    static final float SCREEN_FONT_SIZE = 22;
    static final float SCREEN_X_OFFSET = 5;
    static final float SCREEN_Y_OFFSET = 3;

    /* Modes */
    static final int CURRENT_STATE_MODE = 0;
    static final int NEEDED_TEMPERATURE_MODE  = 1;
    static final int NEEDED_HUMIDITY_MODE = 2;
    static final int ROTATIONS_PER_DAY_MODE = 3;
    static final int CURRENT_PROGRAM_MODE = 4;
    static final int MANUAL_ROTATION_MODE = 5;

    static final int DELTA_MODE = 1;
    static final int N_MODES = 5;
    static final int N_MODES_MANUAL_ROTATION = 6;

    /* Incubator configuration constraints */
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

    /* Animation duration constants */
    static final long CHAMBER_ROTATION_ANIMATION_DURATION = 3000;
    static final long ALARM_BLINKING_DURATION = 500;
    static final long COOLER_ROTATION_DURATION = 100;

    /* Rotation animation constants */
    static final int ROTATION_ANIMATION_POS_LEFT = -45;
    static final int ROTATION_ANIMATION_POS_NEUTRAL = 0;
    static final int ROTATION_ANIMATION_POS_RIGHT = 45;

    /* Cooler phase animation constants */
    static final int DELTA_PHASE = 1;
    static final int N_PHASES = 3;

    private static final int STORAGE_REQUEST = 1;

    /* Program constants */
    static final int DELTA_CURRENT_PROGRAM = 1;

    /* Screen message format strings */
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

    static final String MANUAL_ROTATION_FORMAT =
            " Ручной поворот яиц";

    private static final String LOG_TAG = "Incubator";

    String incubatorAddress = DEFAULT_INCUBATOR_ADDRESS;

    float k;

    ImageView ivIncubatorBody,
            ivIncubatorCooler, ivIncubatorWetter, ivIncubatorHeater, ivIncubatorChamber,
            ivIncubatorScreen;

    ImageView ivIncubatorMinusBtn, ivIncubatorMenuBtn, ivIncubatorPlusBtn,
            ivIncubatorArchive;

    TextView tvIncubatorScreen, tvIncubatorUptime;

    Toolbar toolbar;

    int rotatePhase = 0;
    int heaterAlarmPhase = 0;

    Timer reqTimer, rotTimer, heaterAlarmTimer;
    private int mode = CURRENT_STATE_MODE;

    private IncubatorState state;
    private IncubatorConfig cfg;
    private int oldChamber = IncubatorState.CHAMBER_NEUTRAL;
    private int screenTaps = 0;

    Handler hIncubator, hOverheat, hConfig, hCoolerAnimation, hHeaterAlarm;
    private boolean needConfig = true, manualRotationMode;

    private boolean extStoragePermitted = false;
    private boolean overheatNotified = false;

    SharedPreferences prefs;
    SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    NotificationManager notificationManager;

    Archiver archiver;

    private void writeToArchive() {
        try {
            archiver.writeToArchive(state, cfg);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void makeRequest(String req) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://" + incubatorAddress)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();

        IncubatorRequest incubatorRequest = retrofit.create(IncubatorRequest.class);
        Call<String> call = incubatorRequest.getResponse(req);
        call.timeout().deadline(REQ_TIMEOUT, TimeUnit.MILLISECONDS);
        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                Log.i(LOG_TAG, req);
                String[] strList = response.body().replace("\r\n", "\n").split("\n");

                if (req.startsWith("request_state\r\n")) {
                    oldChamber = state.chamber;
                    state = IncubatorState.deserialize(strList);
                    if (state.overheat)
                        hOverheat.sendEmptyMessage(OVERHEAT_ERROR);
                    else
                        hOverheat.sendEmptyMessage(NO_ERROR);

                    if (!state.power)
                        hIncubator.sendEmptyMessage(INCUBATOR_TURNED_OFF);
                    else
                        hIncubator.sendEmptyMessage(INCUBATOR_ACCESSIBLE);
                } else if (req.startsWith("request_config\r\n")) {
                    IncubatorConfig newCfg = IncubatorConfig.deserialize(strList);
                    if (newCfg.isCorrect()) {
                        cfg = newCfg;
                        updateScreenText();
                        needConfig = false;
                    }
                }

                state.timestamp = new Date().getTime();
                writeToArchive();
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                hIncubator.sendEmptyMessage(INCUBATOR_INACCESSIBLE);
                writeToArchive();
            }
        });
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
            ivIncubatorCooler.setImageResource(R.drawable.ic_incubator_cooler_off);
        }
    }

    void updateWetter() {
        if (state.wetter) {
            ivIncubatorWetter.setImageResource(R.drawable.ic_incubator_wetter_on);
        } else {
            ivIncubatorWetter.setImageResource(R.drawable.ic_incubator_wetter_off);
        }
    }

    void updateHeater() {
        if (state.overheat) {
            if (heaterAlarmPhase == 0) {
                ivIncubatorHeater.setImageResource(R.drawable.ic_incubator_heater_error);
                heaterAlarmPhase = 1;
            } else {
                ivIncubatorHeater.setImageResource(R.drawable.ic_incubator_heater_off);
                heaterAlarmPhase = 0;
            }
        } else if (state.heater) {
            ivIncubatorHeater.setImageResource(R.drawable.ic_incubator_heater_on);
        } else {
            ivIncubatorHeater.setImageResource(R.drawable.ic_incubator_heater_off);
        }
    }

    void updateScreen() {
        if (!state.internet) {
            ivIncubatorScreen.setImageResource(R.drawable.ic_incubator_screen_error);
        } else {
            if (state.power)
                ivIncubatorScreen.setImageResource(R.drawable.ic_incubator_screen_on);
            else
                ivIncubatorScreen.setImageResource(R.drawable.ic_incubator_screen_off);
        }
    }

    void rotateChamber(int pos) {
        int npos = pos;
        if (npos < IncubatorState.CHAMBER_LEFT)
            npos = IncubatorState.CHAMBER_LEFT;
        if (npos > IncubatorState.CHAMBER_RIGHT)
            npos = IncubatorState.CHAMBER_RIGHT;
        switch (npos) {
            case IncubatorState.CHAMBER_LEFT:
                ivIncubatorChamber.animate()
                        .setDuration(CHAMBER_ROTATION_ANIMATION_DURATION)
                        .rotation(ROTATION_ANIMATION_POS_LEFT)
                        .start();
                break;
            case IncubatorState.CHAMBER_NEUTRAL:
                ivIncubatorChamber.animate()
                        .setDuration(CHAMBER_ROTATION_ANIMATION_DURATION)
                        .rotation(ROTATION_ANIMATION_POS_NEUTRAL)
                        .start();
                break;
            case IncubatorState.CHAMBER_RIGHT:
                ivIncubatorChamber.animate()
                        .setDuration(CHAMBER_ROTATION_ANIMATION_DURATION)
                        .rotation(ROTATION_ANIMATION_POS_RIGHT)
                        .start();
                break;
        }
    }

    void updateChamber() {
        if (oldChamber == state.chamber)
            return;

        if (state.chamber == IncubatorState.CHAMBER_ERROR
                || state.chamber == IncubatorState.CHAMBER_UNDEF)
            ivIncubatorChamber.setImageResource(R.drawable.ic_incubator_chamber_error);
        else
            ivIncubatorChamber.setImageResource(R.drawable.ic_incubator_chamber);

        rotateChamber(state.chamber);
        if (state.chamber == IncubatorState.CHAMBER_ERROR)
            ivIncubatorChamber.setRotation(ROTATION_ANIMATION_POS_NEUTRAL);
    }

    void updateUptimeText() {
        if ((state.uptime > 0) && (state.internet) && (state.power)) {
            long n_day = state.uptime / 86400;
            tvIncubatorUptime.setText(
                    String.format(Locale.getDefault(),
                            "%d-й день инкубации",
                            n_day + 1)
            );
        } else {
            tvIncubatorUptime.setText("");
        }
    }

    void updateScreenText() {
        if (!state.power || !state.internet) {
            tvIncubatorScreen.setText("");
            return;
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
            case MANUAL_ROTATION_MODE:
                tvIncubatorScreen.setText(
                        MANUAL_ROTATION_FORMAT
                );
                break;
        }
    }

    void updateIncubator() {
        updateCooler();
        if (!state.overheat)
            updateHeater();
        updateWetter();
        updateChamber();
        updateScreen();

        if (mode == CURRENT_STATE_MODE) {
            updateUptimeText();
            updateScreenText();
        }
    }

    void requestState() {
        makeRequest("request_state\r\n");
    }

    void requestConfig() {
        makeRequest("request_config\r\n");
    }

    void sendConfig() {
        if (!cfg.isCorrect())
            return;

        makeRequest(
                String.format(Locale.US,
                        "needed_temp %.2f\r\n",
                        cfg.neededTemperature
                )
        );
        makeRequest(
                String.format(Locale.US,
                        "needed_humid %.2f\r\n",
                        cfg.neededHumidity
                )
        );
        makeRequest(
                String.format(Locale.US,
                        "rotations_per_day %d\r\n",
                        cfg.rotationsPerDay
                )
        );
        makeRequest(
                String.format(Locale.US,
                        "switch_to_program %d\r\n",
                        cfg.currentProgram
                )
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.incubator_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.config) {
            Intent intent = new Intent(
                    IncubatorStateActivity.this, IncubatorSettingsActivity.class
            );
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == STORAGE_REQUEST)
        {
            extStoragePermitted =
                    (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    && (grantResults[1] == PackageManager.PERMISSION_GRANTED);
            ivIncubatorArchive.setEnabled(true);
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        archiver = new Archiver(getApplicationContext());

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        incubatorAddress = prefs.getString("incubator_address", DEFAULT_INCUBATOR_ADDRESS);
        manualRotationMode = prefs.getBoolean("manual_rotation_mode", false);
        prefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                Log.i(LOG_TAG, "sharedPreferenceChanged@" + key);
                if (key.compareTo("incubator_address") == 0) {
                    incubatorAddress = sharedPreferences.getString(
                            key, DEFAULT_INCUBATOR_ADDRESS
                    );
                    requestConfig();
                    requestState();
                } else if (key.compareTo("manual_rotation_mode") == 0) {
                    manualRotationMode = sharedPreferences.getBoolean(key, false);
                    mode = CURRENT_STATE_MODE;
                    updateIncubator();
                }
            }
        };

        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                    || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED)
            {
                extStoragePermitted = false;
                requestPermissions(new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, STORAGE_REQUEST);
            } else {
                extStoragePermitted = true;
            }
        }

        state = new IncubatorState();
        cfg = new IncubatorConfig();

        hIncubator = new Handler(Looper.myLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case INCUBATOR_INACCESSIBLE:
                        Log.i(LOG_TAG, "INCUBATOR_INACCESIBLE");
                        state.power = true;
                        state.internet = false;
                        needConfig = true;
                        mode = CURRENT_STATE_MODE;

                        updateIncubator();

                        break;
                    case INCUBATOR_ACCESSIBLE:
                        Log.i(LOG_TAG, "INCUBATOR_ACCESSIBLE");
                        if (needConfig)
                            hConfig.sendEmptyMessage(CONFIG_AVAILABLE);
                        state.power = true;
                        state.internet = true;

                        updateIncubator();
                        break;
                    case INCUBATOR_TURNED_OFF:
                        Log.i(LOG_TAG, "INCUBATOR_TURNED_OFF");
                        mode = CURRENT_STATE_MODE;

                        updateIncubator();
                        tvIncubatorScreen.setText("");

                        state.power = false;
                        
                        break;
                }
                return false;
            }
        });

        hConfig = new Handler(Looper.myLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                if (msg.what == CONFIG_AVAILABLE) {
                    Log.i(LOG_TAG, "CONFIG_AVAILABLE");
                    requestConfig();
                }

                return true;
            }
        });

        hOverheat = new Handler(Looper.myLooper(), new Handler.Callback() {

            @Override
            public boolean handleMessage(@NonNull Message msg) {
                if (msg.what == OVERHEAT_ERROR) {
                    Log.i(LOG_TAG, "OVERHEAT_ERROR");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        NotificationChannel channel = new NotificationChannel(
                                "IncubatorOverheatError",
                                "Incubator overheat error channel",
                                NotificationManager.IMPORTANCE_HIGH
                        );
                        notificationManager.createNotificationChannel(channel);
                    }

                    NotificationCompat.Builder bld =
                            new NotificationCompat.Builder(IncubatorStateActivity.this,
                                    "IncubatorOverheatError")
                                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                                    .setContentTitle(getString(R.string.overheat_error_title))
                                    .setContentText(getString(R.string.overheat_error));


                    Notification notification = bld.build();
                    if (!overheatNotified)
                        notificationManager.notify(1, notification);
                    overheatNotified = true;
                } else if (msg.what == NO_ERROR) {
                    overheatNotified = false;
                }
                return true;
            }
        });

        hCoolerAnimation = new Handler(Looper.myLooper(), new Handler.Callback() {

            @Override
            public boolean handleMessage(@NonNull Message msg) {
                if (msg.what == CHANGE_PHASE) {
                    rotatePhase = (rotatePhase + DELTA_PHASE) % N_PHASES;
                    updateCooler();
                }
                return true;
            }
        });

        hHeaterAlarm = new Handler(Looper.myLooper(), new Handler.Callback() {
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

        heaterAlarmTimer = new Timer("IncubatorStateActivity HeaterAlarmTimer");

        reqTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                requestState();

                if (state.internet) {
                    runOnUiThread(new Runnable() {
                                      @Override
                                      public void run() {
                                          updateIncubator();
                                      }
                                  });
                }
            }
        }, 0, REQ_TIMEOUT);

        rotTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (state.cooler) {
                    hCoolerAnimation.sendEmptyMessage(CHANGE_PHASE);
                } else {
                    rotatePhase = 0;
                }
            }
        }, 0, COOLER_ROTATION_DURATION);

        heaterAlarmTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (state.overheat)
                    hHeaterAlarm.sendEmptyMessage(ALARM_HEATER);
            }
        }, 0, ALARM_BLINKING_DURATION);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        toolbar = new Toolbar(this);
        toolbar.setBackgroundResource(R.color.colorPrimary);
        setSupportActionBar(toolbar);
        addContentView(toolbar, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Drawable dIncubatorBody =
                ContextCompat.getDrawable(this, R.drawable.ic_incubator_body);
        Drawable dIncubatorCooler =
                ContextCompat.getDrawable(this, R.drawable.ic_incubator_cooler_off);
        Drawable dIncubatorHeater =
                ContextCompat.getDrawable(this, R.drawable.ic_incubator_heater_off);
        Drawable dIncubatorWetter =
                ContextCompat.getDrawable(this, R.drawable.ic_incubator_wetter_off);
        Drawable dIncubatorChamber =
                ContextCompat.getDrawable(this, R.drawable.ic_incubator_chamber);
        Drawable dIncubatorScreen =
                ContextCompat.getDrawable(this, R.drawable.ic_incubator_screen_off);
        Drawable dIncubatorBtn =
                ContextCompat.getDrawable(this, R.drawable.ic_incubator_minus_btn_normal);
        Drawable dIncubatorArchive =
                ContextCompat.getDrawable(this, R.drawable.ic_incubator_archive_normal);

        int screenWidth = getApplicationContext().getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getApplicationContext().getResources().getDisplayMetrics().heightPixels;
        int screenCenterX = screenWidth / 2;
        int screenCenterY = screenHeight / 2;
        int margin = screenCenterX / 16;

        /* Creating incubator GUI views */

        int incubatorBodyDrawableWidth = dIncubatorBody.getIntrinsicWidth();
        int incubatorBodyDrawableHeight = dIncubatorBody.getIntrinsicHeight();
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
        addContentView(ivIncubatorBody, new ViewGroup.LayoutParams(
                incubatorBodyWidth, incubatorBodyHeight
        ));

        ivIncubatorCooler = new ImageView(this);
        ivIncubatorCooler.setImageResource(R.drawable.ic_incubator_cooler_off);
        ivIncubatorCooler.setX(incubatorX + (COOLER_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorCooler.setY(incubatorY + (COOLER_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorCooler.setVisibility(View.VISIBLE);
        addContentView(ivIncubatorCooler, new ViewGroup.LayoutParams(
                (int)(dIncubatorCooler.getIntrinsicWidth() * k),
                (int)(dIncubatorCooler.getIntrinsicHeight() * k)
        ));

        ivIncubatorWetter = new ImageView(this);
        ivIncubatorWetter.setImageResource(R.drawable.ic_incubator_wetter_off);
        ivIncubatorWetter.setX(incubatorX + (WETTER_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorWetter.setY(incubatorY + (WETTER_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorWetter.setVisibility(View.VISIBLE);
        addContentView(ivIncubatorWetter, new ViewGroup.LayoutParams(
                (int)(dIncubatorWetter.getIntrinsicWidth() * k),
                (int)(dIncubatorWetter.getIntrinsicHeight() * k)
        ));

        ivIncubatorHeater = new ImageView(this);
        ivIncubatorHeater.setImageResource(R.drawable.ic_incubator_heater_off);
        ivIncubatorHeater.setX(incubatorX + (HEATER_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorHeater.setY(incubatorY + (HEATER_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorHeater.setVisibility(View.VISIBLE);
        addContentView(ivIncubatorHeater, new ViewGroup.LayoutParams(
                (int)(dIncubatorHeater.getIntrinsicWidth() * k),
                (int)(dIncubatorHeater.getIntrinsicHeight() * k)
        ));

        ivIncubatorChamber = new ImageView(this);
        ivIncubatorChamber.setImageResource(R.drawable.ic_incubator_chamber);
        ivIncubatorChamber.setX(incubatorX + (CHAMBER_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorChamber.setY(incubatorY + (CHAMBER_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorChamber.setVisibility(View.VISIBLE);
        addContentView(ivIncubatorChamber, new ViewGroup.LayoutParams(
                (int)(dIncubatorChamber.getIntrinsicWidth() * k),
                (int)(dIncubatorChamber.getIntrinsicHeight() * k)
        ));

        ivIncubatorScreen = new ImageView(this);
        ivIncubatorScreen.setImageResource(R.drawable.ic_incubator_screen_off);
        ivIncubatorScreen.setX(incubatorX + (SCREEN_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorScreen.setY(incubatorY + (SCREEN_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorScreen.setVisibility(View.VISIBLE);
        ivIncubatorScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                screenTaps++;
                if (screenTaps < 2)
                    return;

                makeRequest("toggle_incubator\r\n");
                screenTaps = 0;
            }
        });
        addContentView(ivIncubatorScreen, new ViewGroup.LayoutParams(
                (int)(dIncubatorScreen.getIntrinsicWidth() * k),
                (int)(dIncubatorScreen.getIntrinsicHeight() * k)
        ));

        ivIncubatorMinusBtn = new ImageView(this);
        ivIncubatorMinusBtn.setImageResource(R.drawable.ic_incubator_minus_btn);
        ivIncubatorMinusBtn.setX(incubatorX + (MINUS_BTN_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorMinusBtn.setY(incubatorY + (X_BTN_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorMinusBtn.setVisibility(View.VISIBLE);
        ivIncubatorMinusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!state.internet)
                    return;
                switch (mode) {
                    case NEEDED_TEMPERATURE_MODE:
                        if (Math.abs(cfg.neededTemperature - MIN_TEMPERATURE) >= EPSILON) {
                            cfg.neededTemperature -= DELTA_TEMPERATURE;
                            updateScreenText();
                        }
                        break;
                    case NEEDED_HUMIDITY_MODE:
                        if (Math.abs(cfg.neededHumidity - MIN_HUMIDITY) >= EPSILON) {
                            cfg.neededHumidity -= DELTA_HUMIDITY;
                            updateScreenText();
                        }
                        break;
                    case ROTATIONS_PER_DAY_MODE:
                        if ((cfg.rotationsPerDay) >= MIN_ROT_PER_DAY) {
                            cfg.rotationsPerDay -= DELTA_ROT_PER_DAY;
                            updateScreenText();
                        }
                        break;
                    case CURRENT_PROGRAM_MODE:
                        if ((cfg.currentProgram) > 0) {
                            cfg.currentProgram -= DELTA_CURRENT_PROGRAM;
                            updateScreenText();
                        }
                        break;
                }
            }
        });
        ivIncubatorMinusBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (mode == MANUAL_ROTATION_MODE) {
                        makeRequest("rotate_left\r\n");
                        rotateChamber(state.chamber - 1);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (mode == MANUAL_ROTATION_MODE) {
                        makeRequest("rotate_off\r\n");
                    }
                }

                return false;
            }
        });
        addContentView(ivIncubatorMinusBtn, new ViewGroup.LayoutParams(
                (int)(dIncubatorBtn.getIntrinsicWidth() * k),
                (int)(dIncubatorBtn.getIntrinsicHeight() * k)
        ));

        ivIncubatorMenuBtn = new ImageView(this);
        ivIncubatorMenuBtn.setImageResource(R.drawable.ic_incubator_menu_btn);
        ivIncubatorMenuBtn.setX(incubatorX + (MENU_BTN_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorMenuBtn.setY(incubatorY + (X_BTN_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorMenuBtn.setVisibility(View.VISIBLE);
        ivIncubatorMenuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!state.internet)
                    return;
                if (!manualRotationMode)
                    mode = (mode + DELTA_MODE) % N_MODES;
                else
                    mode = (mode + DELTA_MODE) % N_MODES_MANUAL_ROTATION;
                updateScreenText();
                if (mode == CURRENT_STATE_MODE) {
                    sendConfig();
                }
            }
        });
        addContentView(ivIncubatorMenuBtn, new ViewGroup.LayoutParams(
                (int)(dIncubatorBtn.getIntrinsicWidth() * k),
                (int)(dIncubatorBtn.getIntrinsicHeight() * k)
        ));

        ivIncubatorPlusBtn = new ImageView(this);
        ivIncubatorPlusBtn.setImageResource(R.drawable.ic_incubator_plus_btn);
        ivIncubatorPlusBtn.setX(incubatorX + (PLUS_BTN_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorPlusBtn.setY(incubatorY + (X_BTN_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorPlusBtn.setVisibility(View.VISIBLE);
        ivIncubatorPlusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!state.internet)
                    return;

                switch (mode) {
                    case NEEDED_TEMPERATURE_MODE:
                        if (Math.abs(MAX_TEMPERATURE - cfg.neededTemperature) >= EPSILON) {
                            cfg.neededTemperature += DELTA_TEMPERATURE;
                            updateScreenText();
                        }
                        break;
                    case NEEDED_HUMIDITY_MODE:
                        if (Math.abs(MAX_HUMIDITY - cfg.neededHumidity) >= EPSILON) {
                            cfg.neededHumidity += DELTA_HUMIDITY;
                            updateScreenText();
                        }
                        break;
                    case ROTATIONS_PER_DAY_MODE:
                        if ((cfg.rotationsPerDay) <= MAX_ROT_PER_DAY) {
                            cfg.rotationsPerDay += DELTA_ROT_PER_DAY;
                            updateScreenText();
                        }
                        break;
                    case CURRENT_PROGRAM_MODE:
                        if ((cfg.currentProgram) < cfg.numberOfPrograms - 1) {
                            cfg.currentProgram += DELTA_CURRENT_PROGRAM;
                            updateScreenText();
                        }
                        break;
                }
            }
        });
        ivIncubatorPlusBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                     if (mode == MANUAL_ROTATION_MODE) {
                        makeRequest("rotate_right\r\n");
                        rotateChamber(state.chamber + 1);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (mode == MANUAL_ROTATION_MODE) {
                        makeRequest("rotate_off\r\n");
                    }
                }

                return false;
            }
        });
        addContentView(ivIncubatorPlusBtn, new ViewGroup.LayoutParams(
                (int)(dIncubatorBtn.getIntrinsicWidth() * k),
                (int)(dIncubatorBtn.getIntrinsicHeight() * k)
        ));

        tvIncubatorScreen = new TextView(this);
        tvIncubatorScreen.setX(
                incubatorX + (SCREEN_X / BODY_WIDTH) * incubatorBodyWidth + SCREEN_X_OFFSET);
        tvIncubatorScreen.setY(
                incubatorY + (SCREEN_Y / BODY_HEIGHT) * incubatorBodyHeight + SCREEN_Y_OFFSET);
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

        ivIncubatorArchive = new ImageView(this);
        ivIncubatorArchive.setBackgroundResource(R.drawable.ic_incubator_archive);
        ivIncubatorArchive.setX(incubatorX + (ARCHIVE_BTN_X / BODY_WIDTH) * incubatorBodyWidth);
        ivIncubatorArchive.setY(incubatorY + (ARCHIVE_BTN_Y / BODY_HEIGHT) * incubatorBodyHeight);
        ivIncubatorArchive.setVisibility(View.VISIBLE);
        ivIncubatorArchive.setEnabled(extStoragePermitted);
        ivIncubatorArchive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(IncubatorStateActivity.this, ArchiveActivity.class);
                startActivity(intent);
            }
        });
        addContentView(ivIncubatorArchive, new ViewGroup.LayoutParams(
                (int)(dIncubatorArchive.getIntrinsicWidth() * k),
                (int)(dIncubatorArchive.getIntrinsicHeight() * k)
        ));
    }

    @Override
    protected void onStart() {
        super.onStart();
        requestState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
