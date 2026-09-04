package org.gitapk.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import java.util.LinkedHashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String CATALOG_URL =
            "https://raw.githubusercontent.com/carjam120443-netizen/Gitapk/main/catalog/catalog.json";

    private LinearLayout list;
    private File pendingApk;
    private JSONArray catalogApps;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        showHome();
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

    private void setupPage(String titleText, String subtitleText) {
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(32, 40, 32, 32);
        scroll.addView(list);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(32);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        list.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(16);
        list.addView(subtitle);
    }

    private void addNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);

        Button apps = new Button(this);
        apps.setText("Apps");
        apps.setOnClickListener(v -> showHome());
        nav.addView(apps, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button categories = new Button(this);
        categories.setText("Categories");
        categories.setOnClickListener(v -> showCategories());
        nav.addView(categories, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 20, 0, 20);
        list.addView(nav, params);
    }

    private void showHome() {
        setupPage("GitAPK", "Open-source apps from Git repositories");
        addNavigation();
        if (catalogApps != null) showApps(catalogApps, null);
    }

    private void showCategories() {
        setupPage("Categories", "Browse GitAPK apps by category");
        addNavigation();

        if (catalogApps == null) {
            TextView loading = new TextView(this);
            loading.setText("\nLoading categories…");
            loading.setTextSize(18);
            list.addView(loading);
            return;
        }

        Set<String> categories = new LinkedHashSet<>();
        for (int i = 0; i < catalogApps.length(); i++) {
            JSONObject app = catalogApps.optJSONObject(i);
            if (app != null) categories.add(app.optString("category", "Other"));
        }

        for (String category : categories) {
            Button button = new Button(this);
            button.setText(category);
            button.setTextSize(17);
            button.setOnClickListener(v -> showCategory(category));
            list.addView(button);
        }
    }

    private void showCategory(String category) {
        setupPage(category, "Apps in " + category);
        addNavigation();

        Button back = new Button(this);
        back.setText("← All Categories");
        back.setOnClickListener(v -> showCategories());
        list.addView(back);

        if (catalogApps != null) {
            for (int i = 0; i < catalogApps.length(); i++) {
                JSONObject app = catalogApps.optJSONObject(i);
                if (app != null && category.equals(app.optString("category", "Other"))) {
                    try {
                        addAppCard(app);
                    } catch (Exception ignored) { }
                }
            }
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

                catalogApps = new JSONObject(json.toString()).getJSONArray("apps");
                runOnUiThread(() -> {
                    if (list != null && loading.getParent() != null) {
                        showHome();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> loading.setText("\nCould not load the catalog.\n" + e.getMessage()));
            }
        }).start();
    }

    private void showApps(JSONArray apps, TextView loading) {
        if (loading != null && loading.getParent() != null) list.removeView(loading);
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

        TextView category = new TextView(this);
        category.setText("Category: " + app.optString("category", "Other"));
        card.addView(category);

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
