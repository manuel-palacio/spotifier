package net.palacesoft.spotifier;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Spotifier extends Activity {


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                String googleMusicUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (googleMusicUrl != null) {
                    String googleSongId = googleMusicUrl.substring(googleMusicUrl.lastIndexOf("/") + 1);
                    new SpotifyUriTask().execute(googleSongId);
                }
            }
        }

        // new SpotifyUriTask().execute("Tqi2ky3hirnuhaynztacz7d3mai");
    }

    private class SpotifyUriTask extends AsyncTask<String, Void, String> {

        StatusLine statusLine;

        @Override
        protected String doInBackground(String... params) {

            HttpClient httpclient = new DefaultHttpClient();
            StringBuilder sb = new StringBuilder();
            try {
                Uri reqUri = new Uri.Builder()
                        .scheme("http")
                        .authority("spotifier.palace.eu.cloudbees.net")
                        .path("resources/spotifier/" + params[0])
                        .appendQueryParameter("country", getCountryCode())
                        .build();

                HttpResponse response = httpclient.execute(new HttpGet(reqUri.toString()));

                statusLine = response.getStatusLine();

                if (statusLine.getStatusCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

                    String l;
                    while ((l = in.readLine()) != null) {
                        sb.append(l);
                    }
                    in.close();
                } else {
                    sb.append(statusLine.getReasonPhrase());
                }


            } catch (IOException e) {
               sb.append(e.getMessage());
            }


            return sb.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            if (statusLine.getStatusCode() == 200) {
                saveSongUriInClipboard(result);
                showToast("Copied Spotify URL to clipboard");
            } else {
                showToast("Could not fetch Spotify URL: " + result);
            }

            finish();
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }

    private String getCountryCode() {
        TelephonyManager manager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        return manager.getNetworkCountryIso();
    }


    void saveSongUriInClipboard(String spotifyUrl) {

        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("spotify link", spotifyUrl);
        clipboard.setPrimaryClip(clip);
    }

    private void showToast(String message) {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_LONG;

        Toast toast;
        if (context != null) {
            toast = Toast.makeText(context, message, duration);
            toast.show();
        }
    }
}
