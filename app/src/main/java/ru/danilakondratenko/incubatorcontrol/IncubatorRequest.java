package ru.danilakondratenko.incubatorcontrol;

import retrofit2.*;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface IncubatorRequest {
    @POST("/control")
    Call<String> getResponse(@Body String requestCommand);

    @GET("/archive_address")
    Call<String> getArchiveAddress();
}
