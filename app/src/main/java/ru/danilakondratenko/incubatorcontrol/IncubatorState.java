package ru.danilakondratenko.incubatorcontrol;

import android.os.Parcel;
import android.os.Parcelable;

public class IncubatorState implements Parcelable {
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

    IncubatorState() {
        this.currentTemperature = NO_DATA_FLOAT;
        this.currentHumidity = NO_DATA_FLOAT;

        this.wetter = false;
        this.heater = false;
        this.cooler = false;
        this.chamber = CHAMBER_NEUTRAL;
        this.overheat = false;
        this.uptime = 0;
    }

    IncubatorState(float cur_temp, float cur_hum) {
        this.currentTemperature = cur_temp;
        this.currentHumidity = cur_hum;

        this.wetter = false;
        this.heater = false;
        this.cooler = false;
        this.chamber = CHAMBER_NEUTRAL;
        this.overheat = false;
        this.uptime = 0;
    }

    protected IncubatorState(Parcel in) {
        currentTemperature = in.readFloat();
        currentHumidity = in.readFloat();
        wetter = in.readByte() != 0;
        heater = in.readByte() != 0;
        cooler = in.readByte() != 0;
        chamber = in.readInt();
        overheat = in.readByte() != 0;
        uptime = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(currentTemperature);
        dest.writeFloat(currentHumidity);
        dest.writeByte((byte) (wetter ? 1 : 0));
        dest.writeByte((byte) (heater ? 1 : 0));
        dest.writeByte((byte) (cooler ? 1 : 0));
        dest.writeInt(chamber);
        dest.writeByte((byte)(overheat ? 1 : 0));
        dest.writeLong(uptime);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<IncubatorState> CREATOR = new Creator<IncubatorState>() {
        @Override
        public IncubatorState createFromParcel(Parcel in) {
            return new IncubatorState(in);
        }

        @Override
        public IncubatorState[] newArray(int size) {
            return new IncubatorState[size];
        }
    };

    public void clear() {
        this.currentTemperature = NO_DATA_FLOAT;
        this.currentHumidity = NO_DATA_FLOAT;

        this.wetter = false;
        this.heater = false;
        this.cooler = false;
        this.chamber = CHAMBER_NEUTRAL;
        this.overheat = false;
        this.uptime = 0;
    }
}
