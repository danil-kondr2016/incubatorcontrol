package ru.danilakondratenko.incubatorcontrol;

import android.os.AsyncTask;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ArchiveRequestTask extends AsyncTask<String, Void, ArchiveRecord[]> {

    @Override
    protected ArchiveRecord[] doInBackground(String... args) {
        try {
            long mintime = Long.parseLong(args[1]);
            return makeRequest(args[0], mintime);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private ArchiveRecord[] makeRequest(String archiveAddress, long mintime) throws IOException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://" + archiveAddress)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        ArchiveRequest archiveRequest = retrofit.create(ArchiveRequest.class);
        Call<ArchiveRecord[]> call = archiveRequest.getArchive(mintime);
        Response<ArchiveRecord[]> response = call.execute();
        return response.body();
    }
}
