package ru.danilakondratenko.incubatorcontrol;

import retrofit2.*;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface TextRequest {
    @POST("/control")
    Call<String> getResponse(@Body String requestCommand);
}