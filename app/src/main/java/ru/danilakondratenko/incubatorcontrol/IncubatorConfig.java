package ru.danilakondratenko.incubatorcontrol;

import android.os.Parcel;
import android.os.Parcelable;

public class IncubatorConfig implements Parcelable {
    public static final float NO_DATA_FLOAT = Float.NaN;
    public static final int NO_DATA_INT = Integer.MIN_VALUE;

    public float neededTemperature, neededHumidity;
    public int rotationsPerDay, numberOfPrograms, currentProgram;

    IncubatorConfig() {
        this.neededTemperature = NO_DATA_FLOAT;
        this.neededHumidity = NO_DATA_FLOAT;
        this.rotationsPerDay = NO_DATA_INT;
        this.numberOfPrograms = NO_DATA_INT;
        this.currentProgram = NO_DATA_INT;
    }

    protected IncubatorConfig(Parcel in) {
        neededTemperature = in.readFloat();
        neededHumidity = in.readFloat();
        rotationsPerDay = in.readInt();
        numberOfPrograms = in.readInt();
        currentProgram = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(neededTemperature);
        dest.writeFloat(neededHumidity);
        dest.writeInt(rotationsPerDay);
        dest.writeInt(numberOfPrograms);
        dest.writeInt(currentProgram);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<IncubatorConfig> CREATOR = new Creator<IncubatorConfig>() {
        @Override
        public IncubatorConfig createFromParcel(Parcel in) {
            return new IncubatorConfig(in);
        }

        @Override
        public IncubatorConfig[] newArray(int size) {
            return new IncubatorConfig[size];
        }
    };

    public void clear() {
        this.neededTemperature = NO_DATA_FLOAT;
        this.neededHumidity = NO_DATA_FLOAT;
        this.rotationsPerDay = NO_DATA_INT;
        this.numberOfPrograms = NO_DATA_INT;
        this.currentProgram = NO_DATA_INT;
    }
}
