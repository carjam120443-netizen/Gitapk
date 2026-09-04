package org.gitapk.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String CATALOG_URL =
            "https://raw.githubusercontent.com/carjam120443-netizen/Gitapk/main/catalog/catalog.json";

    private LinearLayout list;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(32, 40, 32, 32);
        scroll.addView(list);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("GitAPK");
        title.setTextSize(32);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        list.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Open-source apps from Git repositories");
        subtitle.setTextSize(16);
        list.addView(subtitle);

        loadCatalog();
    }

    private void loadCatalog() {
        TextView loading = new TextView(this);
        loading.setText("\nLoading catalog…");
        loading.setTextSize(18);
        list.addView(loading);

        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(CATALOG_URL).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
                reader.close();
                connection.disconnect();

                JSONArray apps = new JSONObject(json.toString()).getJSONArray("apps");
                runOnUiThread(() -> showApps(apps, loading));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loading.setText("\nCould not load the catalog.\n" + e.getMessage());
                });
            }
        }).start();
    }

    private void showApps(JSONArray apps, TextView loading) {
        list.removeView(loading);
        for (int i = 0; i < apps.length(); i++) {
            try {
                JSONObject app = apps.getJSONObject(i);
                addAppCard(app);
            } catch (Exception ignored) { }
        }
    }

    private void addAppCard(JSONObject app) throws Exception {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);

        TextView name = new TextView(this);
        name.setText(app.optString("name", "Unknown app"));
        name.setTextSize(22);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(name);

        TextView summary = new TextView(this);
        summary.setText(app.optString("summary", ""));
        summary.setTextSize(15);
        card.addView(summary);

        TextView version = new TextView(this);
        version.setText("Version " + app.optString("version", "unknown"));
        card.addView(version);

        Button download = new Button(this);
        download.setText("View APK");
        download.setOnClickListener(v -> {
            String apk = app.optString("apk", "");
            if (!apk.isEmpty()) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apk)));
        });
        card.addView(download);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 24, 0, 0);
        list.addView(card, params);
    }
}
