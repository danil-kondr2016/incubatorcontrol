package ru.danilakondratenko.incubatorcontrol;

import android.icu.util.Output;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class NetworkRequestTask extends AsyncTask<String, Void, String> {
    @Override
    protected String doInBackground(String... args) {
        try {
            return makeRequest(args[0], args[1], args[2], args[3]);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String makeRequest(String address, String method, String mimetype, String stringData) throws Exception {
        try {
            URL url = new URL(address);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            int responseCode;

            connection.setRequestMethod(method);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Connection", "Close");
            if (method.compareTo("GET") == 0) {
                connection.connect();
            } else if (method.compareTo("POST") == 0) {
                connection.setFixedLengthStreamingMode(stringData.getBytes(StandardCharsets.UTF_8).length);
                connection.setRequestProperty("Content-Type", mimetype);
                connection.connect();
                OutputStream out = connection.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
                writer.write(stringData);
                writer.flush();
                writer.close();
                out.close();
            }
            responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String answer = "";
                String line = "";
                InputStream err = null;
                err = connection.getErrorStream();
                if (err == null) {
                    InputStream in = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    while ((line = reader.readLine()) != null) {
                        answer += line + "\r\n";
                    }
                    reader.close();
                    in.close();
                } else {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(err));
                    while ((line = reader.readLine()) != null) {
                        Log.i("Incubator", line);
                    }
                    reader.close();
                    err.close();
                }
                connection.disconnect();
                return answer;
            }
            connection.disconnect();
            return "";
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}