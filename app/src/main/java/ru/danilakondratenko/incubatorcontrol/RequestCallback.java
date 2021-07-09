package ru.danilakondratenko.incubatorcontrol;

public interface RequestCallback {
    void onAnswer(String answer);
    void onFailure();
}
