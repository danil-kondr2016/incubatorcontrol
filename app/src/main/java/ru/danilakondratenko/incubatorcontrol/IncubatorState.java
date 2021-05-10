package ru.danilakondratenko.incubatorcontrol;

public class IncubatorState {
    public static final float NO_DATA_FLOAT = Float.NaN;
    public static final int NO_DATA_INT = Integer.MIN_VALUE;

    public static final int CHAMBER_LEFT = -1;
    public static final int CHAMBER_NEUTRAL = 0;
    public static final int CHAMBER_RIGHT = 1;
    public static final int CHAMBER_ERROR = 2;
    public static final int CHAMBER_UNDEF = 3;

    public float currentTemperature, currentHumidity;

    public boolean wetter, heater, cooler, overheat;
    public int chamber;
    public long uptime;

    public long timestamp;
    public boolean hasInternet;

    IncubatorState() {
        this.currentTemperature = NO_DATA_FLOAT;
        this.currentHumidity = NO_DATA_FLOAT;

        this.wetter = false;
        this.heater = false;
        this.cooler = false;
        this.chamber = CHAMBER_NEUTRAL;
        this.overheat = false;
        this.uptime = 0;
        this.hasInternet = false;
    }

    public void clear() {
        this.currentTemperature = NO_DATA_FLOAT;
        this.currentHumidity = NO_DATA_FLOAT;

        this.wetter = false;
        this.heater = false;
        this.cooler = false;
        this.chamber = CHAMBER_NEUTRAL;
        this.overheat = false;
        this.uptime = 0;
        this.hasInternet = false;
    }
}
