package org.gitapk.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
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
        applyThemeMode();
        showHome();
        loadCatalog();
    }

    private void applyThemeMode() {
        boolean dark = getPreferences(MODE_PRIVATE).getBoolean("dark_mode", false);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
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

    private int color(int id) {
        return getResources().getColor(id);
    }

    private GradientDrawable roundedBackground(int fill, int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(28);
        if (stroke != 0) bg.setStroke(1, stroke);
        return bg;
    }

    private void setupPage(String titleText, String subtitleText) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 24, 24, 32);
        list.setBackgroundColor(color(R.color.gitapk_surface));
        scroll.addView(list);
        setContentView(scroll);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(4, 4, 4, 4);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(30);
        title.setTextColor(color(R.color.gitapk_text));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleBox.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(15);
        subtitle.setTextColor(color(R.color.gitapk_secondary_text));
        titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button menu = new Button(this);
        menu.setText("☰");
        menu.setTextSize(25);
        menu.setContentDescription("Browse categories and settings");
        menu.setMinWidth(0);
        menu.setPadding(4, 0, 4, 0);
        menu.setBackground(roundedBackground(color(R.color.gitapk_card), 0));
        menu.setOnClickListener(v -> showMenu());
        header.addView(menu, new LinearLayout.LayoutParams(58, 58));
        list.addView(header);
    }

    private void showMenu() {
        setupPage("Menu", "Browse GitAPK");

        Button apps = menuButton("▣  All Apps");
        apps.setOnClickListener(v -> showHome());
        list.addView(apps);

        Button categories = menuButton("☰  Categories");
        categories.setOnClickListener(v -> showCategories());
        list.addView(categories);

        boolean dark = getPreferences(MODE_PRIVATE).getBoolean("dark_mode", false);
        Button theme = menuButton(dark ? "☀  Light mode" : "☾  Dark mode");
        theme.setOnClickListener(v -> {
            getPreferences(MODE_PRIVATE).edit().putBoolean("dark_mode", !dark).apply();
            recreate();
        });
        list.addView(theme);
    }

    private Button menuButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(17);
        button.setTextColor(color(R.color.gitapk_text));
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(24, 0, 24, 0);
        button.setBackground(roundedBackground(color(R.color.gitapk_card), 0));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 58);
        p.setMargins(0, 10, 0, 10);
        button.setLayoutParams(p);
        return button;
    }

    private void showHome() {
        setupPage("GitAPK", "Open-source apps from Git repositories");
        if (catalogApps != null) showApps(catalogApps, null);
    }

    private void showCategories() {
        setupPage("Categories", "Find apps by what they do");

        if (catalogApps == null) {
            list.addView(bodyText("Loading categories…"));
            return;
        }

        Set<String> categories = new LinkedHashSet<>();
        for (int i = 0; i < catalogApps.length(); i++) {
            JSONObject app = catalogApps.optJSONObject(i);
            if (app != null) categories.add(app.optString("category", "Other"));
        }

        for (String category : categories) {
            Button button = menuButton("›  " + category);
            button.setOnClickListener(v -> showCategory(category));
            list.addView(button);
        }
    }

    private void showCategory(String category) {
        setupPage(category, "Apps in this category");

        Button back = menuButton("‹  All Categories");
        back.setOnClickListener(v -> showCategories());
        list.addView(back);

        if (catalogApps != null) {
            for (int i = 0; i < catalogApps.length(); i++) {
                JSONObject app = catalogApps.optJSONObject(i);
                if (app != null && category.equals(app.optString("category", "Other"))) {
                    try { addAppCard(app); } catch (Exception ignored) { }
                }
            }
        }
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(17);
        view.setTextColor(color(R.color.gitapk_text));
        view.setPadding(8, 20, 8, 20);
        return view;
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
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
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
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300)
                    throw new Exception("HTTP " + connection.getResponseCode());

                File dir = new File(getCacheDir(), "apk");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create APK cache");
                apk = new File(dir, "gitapk-download.apk");
                try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                }
                connection.disconnect();
                File finalApk = apk;
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText("Install");
                    if (canInstallPackages()) installApk(finalApk); else requestInstallPermission(finalApk);
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
        TextView loading = bodyText("Loading catalog…");
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
                runOnUiThread(() -> showHome());
            } catch (Exception e) {
                runOnUiThread(() -> loading.setText("Could not load the catalog.\n" + e.getMessage()));
            }
        }).start();
    }

    private void showApps(JSONArray apps, TextView loading) {
        if (loading != null && loading.getParent() != null) list.removeView(loading);
        for (int i = 0; i < apps.length(); i++) {
            try { addAppCard(apps.getJSONObject(i)); } catch (Exception ignored) { }
        }
    }

    private void addAppCard(JSONObject app) throws Exception {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 22, 24, 22);
        card.setBackground(roundedBackground(color(R.color.gitapk_card), 0));

        TextView name = bodyText(app.optString("name", "Unknown app"));
        name.setTextSize(21);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setPadding(0, 0, 0, 6);
        card.addView(name);

        TextView summary = bodyText(app.optString("summary", ""));
        summary.setTextSize(15);
        summary.setPadding(0, 0, 0, 8);
        card.addView(summary);

        TextView meta = bodyText(app.optString("category", "Other") + "  •  Version " + app.optString("version", "unknown"));
        meta.setTextSize(14);
        meta.setTextColor(color(R.color.gitapk_secondary_text));
        meta.setPadding(0, 0, 0, 12);
        card.addView(meta);

        Button install = new Button(this);
        install.setText("Install");
        install.setTextSize(16);
        install.setOnClickListener(v -> downloadAndInstall(app.optString("apk", ""), install));
        card.addView(install);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 14, 0, 0);
        list.addView(card, params);
    }
}
