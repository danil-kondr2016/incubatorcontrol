package ru.danilakondratenko.incubatorcontrol;

import java.util.Locale;

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
    public boolean internet, power;

    public boolean isChanged;

    IncubatorState() {
        this.currentTemperature = NO_DATA_FLOAT;
        this.currentHumidity = NO_DATA_FLOAT;

        this.wetter = false;
        this.heater = false;
        this.cooler = false;
        this.chamber = CHAMBER_NEUTRAL;
        this.overheat = false;
        this.uptime = 0;
        this.internet = false;
        this.power = false;
        this.isChanged = false;
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
        this.internet = false;
        this.power = false;
        this.isChanged = false;
    }

    public String[] serialize() {
        String result = "";

        if (this.power) {
            result += String.format(Locale.US, "current_temp %.2f\r\n|", this.currentTemperature);
            result += String.format(Locale.US, "current_humid %.2f\r\n|", this.currentHumidity);
            result += String.format(Locale.US, "heater %d\r\n|", this.heater ? 1 : 0);
            result += String.format(Locale.US, "cooler %d\r\n|", this.cooler ? 1 : 0);
            result += String.format(Locale.US, "wetter %d\r\n|", this.wetter ? 1 : 0);
            result += String.format(Locale.US, "chamber %d\r\n|", this.chamber);
            result += String.format(Locale.US, "uptime %d\r\n|", this.uptime);
            if (this.isChanged)
                result += "changed\r\n|";
            if (this.overheat)
                result += "overheat\r\n|";
        } else {
            result += "turned_off\r\n|";
        }

        return result.split("\\|");
    }

    public static IncubatorState deserialize(String[] strs) {
        IncubatorState result = new IncubatorState();
        result.power = true;
        for (String x : strs) {
            String[] args = x.trim().split(" ");
            if (args[0].compareTo("current_temp") == 0) {
                if (args[1].compareToIgnoreCase("nan") != 0)
                    result.currentTemperature = Float.parseFloat(args[1]);
                else
                    result.currentTemperature = NO_DATA_FLOAT;
            } else if (args[0].compareTo("current_humid") == 0) {
                if (args[1].compareToIgnoreCase("nan") != 0)
                    result.currentHumidity = Float.parseFloat(args[1]);
                else
                    result.currentHumidity = NO_DATA_FLOAT;
            } else if (args[0].compareTo("heater") == 0) {
                result.heater = (Integer.parseInt(args[1]) > 0);
            } else if (args[0].compareTo("cooler") == 0) {
                result.cooler = (Integer.parseInt(args[1]) > 0);
            } else if (args[0].compareTo("wetter") == 0) {
                result.wetter = (Integer.parseInt(args[1]) > 0);
            } else if (args[0].compareTo("chamber") == 0) {
                result.chamber = Integer.parseInt(args[1]);
            } else if (args[0].compareTo("uptime") == 0) {
                result.uptime = Long.parseLong(args[1]);
            } else if (args[0].compareTo("overheat") == 0) {
                result.overheat = true;
            }else if(args[0].compareTo("turned_off") == 0) {
                result.power =false;
            } else if (args[0].compareTo("changed") == 0) {
                result.isChanged = true;
            }
        }
        return result;
    }

    public boolean isCorrect() {
        if (Float.isNaN(this.currentTemperature))
            return false;
        if (Float.isNaN(this.currentHumidity))
            return false;
        return true;
    }
}
