package ru.danilakondratenko.incubatorcontrol;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ArchiveRecord {
    public static final float NO_DATA_FLOAT = Float.NaN;
    public static final int NO_DATA_INT = Integer.MIN_VALUE;

    public static final int CHAMBER_LEFT = -1;
    public static final int CHAMBER_NEUTRAL = 0;
    public static final int CHAMBER_RIGHT = 1;
    public static final int CHAMBER_ERROR = 2;
    public static final int CHAMBER_UNDEF = 3;

    @SerializedName("timestamp")
    @Expose
    public long timestamp;

    @SerializedName("current_temp")
    @Expose
    public float currentTemperature;

    @SerializedName("current_humid")
    @Expose
    public float currentHumidity;

    @SerializedName("needed_temp")
    @Expose
    public float neededTemperature;

    @SerializedName("needed_humid")
    @Expose
    public float neededHumidity;

    @SerializedName("heater")
    @Expose
    public int heater;

    @SerializedName("wetter")
    @Expose
    public int wetter;

    @SerializedName("chamber")
    @Expose
    public int chamber;

    ArchiveRecord() {
        this.timestamp = 0;
        this.currentTemperature = 0;
        this.currentHumidity = 0;
        this.neededTemperature = 0;
        this.neededHumidity = 0;
        this.heater = 0;
        this.wetter = 0;
        this.chamber = CHAMBER_NEUTRAL;
    }

    ArchiveRecord(ArchiveRecord record) {
        this.timestamp = record.timestamp;
        this.currentTemperature = record.currentTemperature;
        this.currentHumidity = record.currentHumidity;
        this.neededTemperature = record.neededTemperature;
        this.neededHumidity = record.neededHumidity;
        this.heater = record.heater;
        this.wetter = record.wetter;
        this.chamber = record.chamber;
    }
}
