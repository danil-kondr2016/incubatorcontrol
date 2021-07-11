package ru.danilakondratenko.incubatorcontrol;

import retrofit2.Call;
import retrofit2.http.GET;

public interface VideoArchiveRequest {
    @GET("/archivelist")
    Call<String> getArchiveList();
}
