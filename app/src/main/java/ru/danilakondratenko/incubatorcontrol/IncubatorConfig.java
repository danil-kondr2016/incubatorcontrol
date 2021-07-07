package ru.danilakondratenko.incubatorcontrol;

import java.util.Locale;

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

    public String[] serialize() {
        String result = "";

        result += String.format(Locale.US, "needed_temp %.2f\r\n|", this.neededTemperature);
        result += String.format(Locale.US, "needed_humid %.2f\r\n|", this.neededHumidity);
        result += String.format(Locale.US, "rotations_per_day %d\r\n|", this.rotationsPerDay);
        result += String.format(Locale.US, "number_of_programs %d\r\n|", this.numberOfPrograms);
        result += String.format(Locale.US, "current_program %d\r\n|", this.currentProgram);

        return result.split("\\|");
    }

    public String[] serializeToSend() {
        String result = "";

        result += String.format(Locale.US, "needed_temp %.2f\r\n|", this.neededTemperature);
        result += String.format(Locale.US, "needed_humid %.2f\r\n|", this.neededHumidity);
        result += String.format(Locale.US, "rotations_per_day %d\r\n|", this.rotationsPerDay);
        result += String.format(Locale.US, "switch_to_program %d\r\n|", this.currentProgram);

        return result.split("\\|");
    }

    public static IncubatorConfig deserialize(String[] strs) {
        IncubatorConfig result = new IncubatorConfig();

        for (String x : strs) {
            String[] args = x.trim().split(" ");
            if (args[0].compareTo("needed_temp") == 0) {
                if (args[1].compareToIgnoreCase("nan") != 0)
                    result.neededTemperature = Float.parseFloat(args[1]);
                else
                    result.neededTemperature = NO_DATA_FLOAT;
            } else if (args[0].compareTo("needed_humid") == 0) {
                if (args[1].compareToIgnoreCase("nan") != 0)
                    result.neededHumidity = Float.parseFloat(args[1]);
                else
                    result.neededHumidity = NO_DATA_FLOAT;
            } else if (args[0].compareTo("rotations_per_day") == 0) {
                result.rotationsPerDay = Integer.parseInt(args[1]);
            } else if (args[0].compareTo("number_of_programs") == 0) {
                result.numberOfPrograms = Integer.parseInt(args[1]);
            } else if (args[0].compareTo("current_program") == 0) {
                result.currentProgram = Integer.parseInt(args[1]);
            }
        }

        return result;
    }

    public boolean isCorrect() {
        if (Float.isNaN(this.neededTemperature))
            return false;
        if (Float.isNaN(this.neededHumidity))
            return false;
        return true;
    }
}
