package org.gitapk.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
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
    private EditText searchBox;
    private String selectedCategory = "All";

    @Override
    public void onCreate(Bundle state) {
        applyThemeMode();
        super.onCreate(state);
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

    private GradientDrawable roundedBackground(int fill, int stroke, float radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(radius);
        if (stroke != 0) bg.setStroke(1, stroke);
        return bg;
    }

    private GradientDrawable pillBackground(int fill) {
        return roundedBackground(fill, 0, 1000);
    }

    private TextView label(String text, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color(R.color.gitapk_text));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void setupPage(String titleText, String subtitleText) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(20, 18, 20, 36);
        list.setBackgroundColor(color(R.color.gitapk_surface));
        scroll.addView(list);
        setContentView(scroll);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView title = label(titleText, 28, true);
        brand.addView(title);
        TextView subtitle = label(subtitleText, 14, false);
        subtitle.setTextColor(color(R.color.gitapk_secondary_text));
        subtitle.setPadding(0, 2, 0, 0);
        brand.addView(subtitle);
        top.addView(brand, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button menu = new Button(this);
        menu.setText("☰");
        menu.setTextSize(22);
        menu.setContentDescription("Open GitAPK menu");
        menu.setMinWidth(0);
        menu.setPadding(0, 0, 0, 0);
        menu.setBackground(pillBackground(color(R.color.gitapk_card)));
        menu.setOnClickListener(v -> showMenu());
        top.addView(menu, new LinearLayout.LayoutParams(52, 52));
        list.addView(top);
    }

    private void showHome() {
        selectedCategory = "All";
        setupPage("GitAPK", "Discover open-source Android apps");

        if (catalogApps == null) {
            list.addView(bodyText("Loading catalog…"));
            return;
        }

        TextView hero = label("Your open-source app store", 20, true);
        hero.setPadding(4, 24, 4, 4);
        list.addView(hero);

        TextView heroSub = label("Browse apps from GitHub and F-Droid sources.", 14, false);
        heroSub.setTextColor(color(R.color.gitapk_secondary_text));
        heroSub.setPadding(4, 0, 4, 14);
        list.addView(heroSub);

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextSize(16);
        searchBox.setHint("Search apps…");
        searchBox.setPadding(18, 0, 18, 0);
        searchBox.setBackground(roundedBackground(color(R.color.gitapk_card), color(R.color.gitapk_outline), 30));
        list.addView(searchBox, new LinearLayout.LayoutParams(-1, 56));

        TextView section = label("Browse categories", 18, true);
        section.setPadding(4, 22, 4, 8);
        list.addView(section);
        addCategoryChips();

        TextView appsTitle = label("All apps", 20, true);
        appsTitle.setPadding(4, 22, 4, 2);
        list.addView(appsTitle);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshAppList(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        refreshAppList();
    }

    private void addCategoryChips() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(2, 2, 2, 2);

        addChip(row, "All");
        Set<String> categories = new LinkedHashSet<>();
        for (int i = 0; i < catalogApps.length(); i++) {
            JSONObject app = catalogApps.optJSONObject(i);
            if (app != null) categories.add(app.optString("category", "Other"));
        }
        for (String category : categories) addChip(row, category);
        scroll.addView(row);
        list.addView(scroll);
    }

    private void addChip(LinearLayout row, String category) {
        Button chip = new Button(this);
        chip.setText(category);
        chip.setTextSize(14);
        chip.setAllCaps(false);
        chip.setMinHeight(44);
        chip.setMinWidth(0);
        chip.setPadding(18, 0, 18, 0);
        chip.setTextColor(color(R.color.gitapk_text));
        chip.setBackground(pillBackground(category.equals(selectedCategory)
                ? color(R.color.gitapk_accent_soft) : color(R.color.gitapk_card)));
        chip.setOnClickListener(v -> {
            selectedCategory = category;
            showHome();
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, 44);
        p.setMargins(0, 0, 8, 0);
        row.addView(chip, p);
    }

    private void refreshAppList() {
        if (catalogApps == null || list == null) return;
        // Rebuild the home page after a search/category change while preserving the query.
        String query = searchBox == null ? "" : searchBox.getText().toString();
        int start = findAppSectionStart();
        if (start < 0) return;
        while (list.getChildCount() > start) list.removeViewAt(start);

        int shown = 0;
        for (int i = 0; i < catalogApps.length(); i++) {
            JSONObject app = catalogApps.optJSONObject(i);
            if (app == null) continue;
            String category = app.optString("category", "Other");
            String name = app.optString("name", "");
            String summary = app.optString("summary", "");
            boolean categoryMatch = "All".equals(selectedCategory) || selectedCategory.equals(category);
            boolean searchMatch = query.trim().isEmpty()
                    || name.toLowerCase().contains(query.trim().toLowerCase())
                    || summary.toLowerCase().contains(query.trim().toLowerCase());
            if (categoryMatch && searchMatch) {
                try { addAppCard(app); shown++; } catch (Exception ignored) { }
            }
        }
        if (shown == 0) list.addView(bodyText("No apps found. Try another search or category."));
    }

    private int findAppSectionStart() {
        for (int i = 0; i < list.getChildCount(); i++) {
            View view = list.getChildAt(i);
            if (view instanceof TextView && "All apps".contentEquals(((TextView) view).getText())) return i + 1;
        }
        return -1;
    }

    private void showMenu() {
        setupPage("Menu", "Everything in GitAPK");

        Button apps = menuButton("⌂   All apps");
        apps.setOnClickListener(v -> showHome());
        list.addView(apps);

        Button categories = menuButton("▦   Browse categories");
        categories.setOnClickListener(v -> showCategories());
        list.addView(categories);

        boolean dark = getPreferences(MODE_PRIVATE).getBoolean("dark_mode", false);
        Button theme = menuButton(dark ? "☀   Switch to light mode" : "☾   Switch to dark mode");
        theme.setOnClickListener(v -> {
            getPreferences(MODE_PRIVATE).edit().putBoolean("dark_mode", !dark).apply();
            recreate();
        });
        list.addView(theme);
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
            Button button = menuButton("›   " + category);
            button.setOnClickListener(v -> showCategory(category));
            list.addView(button);
        }
    }

    private void showCategory(String category) {
        setupPage(category, "Apps in this category");
        Button back = menuButton("‹   All categories");
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

    private Button menuButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTextColor(color(R.color.gitapk_text));
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(22, 0, 22, 0);
        button.setBackground(roundedBackground(color(R.color.gitapk_card), color(R.color.gitapk_outline), 22));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 58);
        p.setMargins(0, 7, 0, 7);
        button.setLayoutParams(p);
        return button;
    }

    private TextView bodyText(String text) {
        TextView view = label(text, 16, false);
        view.setPadding(8, 20, 8, 20);
        return view;
    }

    private void addAppCard(JSONObject app) throws Exception {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20, 18, 20, 18);
        card.setBackground(roundedBackground(color(R.color.gitapk_card), 0, 24));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = label("●", 25, true);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(color(R.color.gitapk_accent));
        icon.setBackground(pillBackground(color(R.color.gitapk_accent_soft)));
        top.addView(icon, new LinearLayout.LayoutParams(48, 48));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(14, 0, 0, 0);
        TextView name = label(app.optString("name", "Unknown app"), 18, true);
        titleBox.addView(name);
        TextView meta = label(app.optString("category", "Other") + "  •  v" + app.optString("version", "unknown"), 13, false);
        meta.setTextColor(color(R.color.gitapk_secondary_text));
        titleBox.addView(meta);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(top);

        TextView summary = label(app.optString("summary", "No description available."), 14, false);
        summary.setTextColor(color(R.color.gitapk_secondary_text));
        summary.setPadding(0, 14, 0, 14);
        card.addView(summary);

        Button install = new Button(this);
        install.setText("Install");
        install.setTextSize(14);
        install.setAllCaps(false);
        install.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        install.setTextColor(color(R.color.gitapk_button_text));
        install.setBackground(pillBackground(color(R.color.gitapk_accent)));
        install.setOnClickListener(v -> downloadAndInstall(app.optString("apk", ""), install));
        card.addView(install, new LinearLayout.LayoutParams(-1, 48));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 10, 0, 0);
        list.addView(card, params);
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls();
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
        if (list.getChildCount() == 0) list.addView(loading);
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
                runOnUiThread(this::showHome);
            } catch (Exception e) {
                runOnUiThread(() -> loading.setText("Could not load the catalog.\n" + e.getMessage()));
            }
        }).start();
    }
}
