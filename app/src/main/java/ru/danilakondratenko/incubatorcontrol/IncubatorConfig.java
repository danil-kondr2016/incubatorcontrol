package ru.danilakondratenko.incubatorcontrol;

public class IncubatorConfig {
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

    public void clear() {
        this.neededTemperature = NO_DATA_FLOAT;
        this.neededHumidity = NO_DATA_FLOAT;
        this.rotationsPerDay = NO_DATA_INT;
        this.numberOfPrograms = NO_DATA_INT;
        this.currentProgram = NO_DATA_INT;
    }
}
