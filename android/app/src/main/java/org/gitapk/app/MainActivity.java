package org.gitapk.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
    private static final String CATALOG_URL = "https://raw.githubusercontent.com/carjam120443-netizen/Gitapk/main/catalog/catalog.json";
    private LinearLayout list;
    private JSONArray catalogApps;
    private File pendingApk;
    private EditText searchBox;
    private String selectedCategory = "All";

    @Override public void onCreate(Bundle state) {
        applyThemeMode(); super.onCreate(state); showHome(); loadCatalog();
    }

    private void applyThemeMode() {
        boolean dark = getPreferences(MODE_PRIVATE).getBoolean("dark_mode", false);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
    }

    @Override protected void onResume() {
        super.onResume();
        if (pendingApk != null && pendingApk.exists() && canInstallPackages()) {
            File apk = pendingApk; pendingApk = null; installApk(apk);
        }
    }

    private int color(int id) { return getResources().getColor(id); }
    private GradientDrawable bg(int fill, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(radius); return d;
    }
    private TextView text(String s, float size, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color(R.color.gitapk_text));
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }

    private void setupPage(String title, String subtitle) {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(20,18,20,36);
        list.setBackgroundColor(color(R.color.gitapk_surface)); scroll.addView(list); setContentView(scroll);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(text(title,28,true)); TextView sub=text(subtitle,14,false); sub.setTextColor(color(R.color.gitapk_secondary_text)); brand.addView(sub);
        top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button menu = new Button(this); menu.setText("☰"); menu.setTextSize(22); menu.setAllCaps(false); menu.setMinWidth(0); menu.setPadding(0,0,0,0);
        menu.setContentDescription("Open GitAPK menu"); menu.setBackground(bg(color(R.color.gitapk_card),1000)); menu.setOnClickListener(v->showMenu());
        top.addView(menu,new LinearLayout.LayoutParams(52,52)); list.addView(top);
    }

    private void showHome() {
        setupPage("GitAPK", "Discover open-source Android apps");
        if (catalogApps == null) { list.addView(body("Loading catalog…")); return; }
        TextView hero=text("Your open-source app store",20,true); hero.setPadding(4,24,4,4); list.addView(hero);
        TextView hs=text("Browse apps from GitHub and F-Droid sources.",14,false); hs.setTextColor(color(R.color.gitapk_secondary_text)); hs.setPadding(4,0,4,14); list.addView(hs);
        searchBox=new EditText(this); searchBox.setSingleLine(true); searchBox.setTextSize(16); searchBox.setHint("Search apps…"); searchBox.setPadding(18,0,18,0);
        searchBox.setBackground(bg(color(R.color.gitapk_card),30)); list.addView(searchBox,new LinearLayout.LayoutParams(-1,56));
        TextView catTitle=text("Browse categories",18,true); catTitle.setPadding(4,22,4,8); list.addView(catTitle); addCategoryChips();
        TextView appsTitle=text(selectedCategory.equals("All") ? "All apps" : selectedCategory+" apps",20,true); appsTitle.setPadding(4,22,4,2); list.addView(appsTitle);
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){refreshAppList();}
            public void afterTextChanged(android.text.Editable e){}
        });
        refreshAppList();
    }

    private void addCategoryChips() {
        HorizontalScrollView scroll=new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this); row.setPadding(2,2,2,2); addChip(row,"All");
        Set<String> cats=new LinkedHashSet<>();
        for(int i=0;i<catalogApps.length();i++){ JSONObject a=catalogApps.optJSONObject(i); if(a!=null) cats.add(a.optString("category","Other")); }
        for(String c:cats) addChip(row,c); scroll.addView(row); list.addView(scroll);
    }

    private void addChip(LinearLayout row,String category) {
        Button chip=new Button(this); chip.setText(category); chip.setTextSize(14); chip.setAllCaps(false); chip.setMinHeight(44); chip.setMinWidth(0); chip.setPadding(18,0,18,0);
        chip.setTextColor(color(R.color.gitapk_text)); chip.setBackground(bg(category.equals(selectedCategory)?color(R.color.gitapk_accent_soft):color(R.color.gitapk_card),1000));
        chip.setOnClickListener(v->{selectedCategory=category; showHome();});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,44); p.setMargins(0,0,8,0); row.addView(chip,p);
    }

    private void refreshAppList() {
        if(catalogApps==null||list==null)return; int start=findAppSectionStart(); if(start<0)return;
        while(list.getChildCount()>start)list.removeViewAt(start);
        String q=searchBox==null?"":searchBox.getText().toString().trim().toLowerCase(); int shown=0;
        for(int i=0;i<catalogApps.length();i++){
            JSONObject a=catalogApps.optJSONObject(i); if(a==null)continue;
            String cat=a.optString("category","Other"), name=a.optString("name",""), sum=a.optString("summary","");
            boolean cm="All".equals(selectedCategory)||selectedCategory.equals(cat); boolean sm=q.isEmpty()||name.toLowerCase().contains(q)||sum.toLowerCase().contains(q);
            if(cm&&sm){try{addAppCard(a);shown++;}catch(Exception ignored){}}
        }
        if(shown==0)list.addView(body("No apps found. Try another search or category."));
    }

    private int findAppSectionStart(){
        for(int i=0;i<list.getChildCount();i++){View v=list.getChildAt(i);if(v instanceof TextView){String s=((TextView)v).getText().toString();if("All apps".equals(s)||s.endsWith(" apps"))return i+1;}}
        return -1;
    }

    private void showMenu(){
        setupPage("Menu","Everything in GitAPK");
        Button apps=menuButton("⌂   All apps"); apps.setOnClickListener(v->{selectedCategory="All";showHome();}); list.addView(apps);
        Button cats=menuButton("▦   Browse categories"); cats.setOnClickListener(v->showCategories()); list.addView(cats);
        boolean dark=getPreferences(MODE_PRIVATE).getBoolean("dark_mode",false);
        Button theme=menuButton(dark?"☀   Switch to light mode":"☾   Switch to dark mode"); theme.setOnClickListener(v->{getPreferences(MODE_PRIVATE).edit().putBoolean("dark_mode",!dark).apply();recreate();}); list.addView(theme);
    }

    private void showCategories(){
        setupPage("Categories","Find apps by what they do"); if(catalogApps==null){list.addView(body("Loading categories…"));return;}
        Set<String> cats=new LinkedHashSet<>(); for(int i=0;i<catalogApps.length();i++){JSONObject a=catalogApps.optJSONObject(i);if(a!=null)cats.add(a.optString("category","Other"));}
        for(String c:cats){Button b=menuButton("›   "+c);b.setOnClickListener(v->showCategory(c));list.addView(b);}
    }

    private void showCategory(String category){ selectedCategory=category; showHome(); }

    private Button menuButton(String s){
        Button b=new Button(this);b.setText(s);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(color(R.color.gitapk_text));b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPadding(22,0,22,0);
        b.setBackground(bg(color(R.color.gitapk_card),22));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,58);p.setMargins(0,7,0,7);b.setLayoutParams(p);return b;
    }

    private TextView body(String s){TextView v=text(s,16,false);v.setPadding(8,20,8,20);return v;}

    private void addAppCard(JSONObject app)throws Exception{
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(20,18,20,18);card.setBackground(bg(color(R.color.gitapk_card),24));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon=text("●",25,true);icon.setGravity(Gravity.CENTER);icon.setTextColor(color(R.color.gitapk_accent));icon.setBackground(bg(color(R.color.gitapk_accent_soft),1000));top.addView(icon,new LinearLayout.LayoutParams(48,48));
        LinearLayout tb=new LinearLayout(this);tb.setOrientation(LinearLayout.VERTICAL);tb.setPadding(14,0,0,0);tb.addView(text(app.optString("name","Unknown app"),18,true));
        TextView meta=text(app.optString("category","Other")+"  •  v"+app.optString("version","unknown"),13,false);meta.setTextColor(color(R.color.gitapk_secondary_text));tb.addView(meta);top.addView(tb,new LinearLayout.LayoutParams(0,-2,1));card.addView(top);
        TextView sum=text(app.optString("summary","No description available."),14,false);sum.setTextColor(color(R.color.gitapk_secondary_text));sum.setPadding(0,14,0,14);card.addView(sum);
        Button install=new Button(this);install.setText("Install");install.setTextSize(14);install.setAllCaps(false);install.setTypeface(Typeface.DEFAULT,Typeface.BOLD);install.setTextColor(color(R.color.gitapk_button_text));install.setBackground(bg(color(R.color.gitapk_accent),1000));install.setOnClickListener(v->downloadAndInstall(app.optString("apk",""),install));card.addView(install,new LinearLayout.LayoutParams(-1,48));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,10,0,0);list.addView(card,p);
    }

    private boolean canInstallPackages(){return Build.VERSION.SDK_INT<Build.VERSION_CODES.O||getPackageManager().canRequestPackageInstalls();}
    private void requestInstallPermission(File apk){pendingApk=apk;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}
    private void installApk(File apk){try{Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",apk);Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"application/vnd.android.package-archive");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}catch(Exception e){Toast.makeText(this,"Could not open installer: "+e.getMessage(),Toast.LENGTH_LONG).show();}}

    private void downloadAndInstall(String apkUrl,Button button){
        if(apkUrl==null||apkUrl.isEmpty()){Toast.makeText(this,"This app has no APK download yet.",Toast.LENGTH_SHORT).show();return;}
        button.setEnabled(false);button.setText("Downloading…");
        new Thread(()->{File apk=null;try{
            HttpURLConnection c=(HttpURLConnection)new URL(apkUrl).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(true);c.connect();
            if(c.getResponseCode()<200||c.getResponseCode()>=300)throw new Exception("HTTP "+c.getResponseCode());
            File dir=new File(getCacheDir(),"apk");if(!dir.exists()&&!dir.mkdirs())throw new Exception("Could not create APK cache");apk=new File(dir,"gitapk-download.apk");
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(apk)){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}c.disconnect();File finalApk=apk;
            runOnUiThread(()->{button.setEnabled(true);button.setText("Install");if(canInstallPackages())installApk(finalApk);else requestInstallPermission(finalApk);});
        }catch(Exception e){if(apk!=null)apk.delete();String m=e.getMessage()==null?"Download failed":e.getMessage();runOnUiThread(()->{button.setEnabled(true);button.setText("Install");Toast.makeText(this,"Download failed: "+m,Toast.LENGTH_LONG).show();});}}).start();
    }

    private void loadCatalog(){
        TextView loading=body("Loading catalog…");list.addView(loading);new Thread(()->{try{
            HttpURLConnection c=(HttpURLConnection)new URL(CATALOG_URL).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(10000);InputStream in=c.getInputStream();StringBuilder json=new StringBuilder();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)json.append(new String(buf,0,n));in.close();c.disconnect();
            catalogApps=new JSONObject(json.toString()).getJSONArray("apps");runOnUiThread(this::showHome);
        }catch(Exception e){runOnUiThread(()->loading.setText("Could not load the catalog.\n"+e.getMessage()));}}).start();
    }
}
