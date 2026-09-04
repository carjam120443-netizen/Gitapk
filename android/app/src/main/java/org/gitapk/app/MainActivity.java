package org.gitapk.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String CATALOG_URL =
            "https://raw.githubusercontent.com/carjam120443-netizen/Gitapk/main/catalog/catalog.json";

    private LinearLayout list;
    private File pendingApk;

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

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingApk != null && pendingApk.exists() && canInstallPackages()) {
            File apk = pendingApk;
            pendingApk = null;
            installApk(apk);
        }
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getPackageManager().canRequestPackageInstalls();
    }

    private void requestInstallPermission(File apk) {
        pendingApk = apk;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(settings);
        }
    }

    private void installApk(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void downloadAndInstall(String apkUrl, Button button) {
        if (apkUrl == null || apkUrl.isEmpty()) {
            Toast.makeText(this, "This app has no APK download yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        button.setEnabled(false);
        button.setText("Downloading…");

        new Thread(() -> {
            File apk = null;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(apkUrl).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                    throw new Exception("HTTP " + connection.getResponseCode());
                }

                File dir = new File(getCacheDir(), "apk");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create APK cache");
                apk = new File(dir, "gitapk-download.apk");

                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                }
                connection.disconnect();

                File finalApk = apk;
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText("Install");
                    if (canInstallPackages()) installApk(finalApk);
                    else requestInstallPermission(finalApk);
                });
            } catch (Exception e) {
                if (apk != null) apk.delete();
                String message = e.getMessage() == null ? "Download failed" : e.getMessage();
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText("Install");
                    Toast.makeText(this, "Download failed: " + message, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
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
                InputStream input = connection.getInputStream();
                StringBuilder json = new StringBuilder();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) json.append(new String(buffer, 0, read));
                input.close();
                connection.disconnect();

                JSONArray apps = new JSONObject(json.toString()).getJSONArray("apps");
                runOnUiThread(() -> showApps(apps, loading));
            } catch (Exception e) {
                runOnUiThread(() -> loading.setText("\nCould not load the catalog.\n" + e.getMessage()));
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

        Button install = new Button(this);
        install.setText("Install");
        install.setOnClickListener(v -> downloadAndInstall(app.optString("apk", ""), install));
        card.addView(install);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 24, 0, 0);
        list.addView(card, params);
    }
}
