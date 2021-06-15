package ru.danilakondratenko.incubatorcontrol;

import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ArchiveRequest {
    @GET("/archive/query.php")
    Call<ArchiveRecord[]> getArchive(@Query("mintime") long minTime);
}
