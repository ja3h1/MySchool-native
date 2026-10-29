package com.myschool.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import kotlin.Unit;

public class MasaoLmsActivity extends AppCompatActivity {
    static final String BASE_URL = "https://masaolms.carsu.edu.ph";
    static final String LOGIN_URL = BASE_URL + "/login/index.php";
    static final String DASHBOARD_URL = "/my/";
    static final String AJAX_PATH = "/lib/ajax/service.php";

    static final int RESULT_POLL_MS = 300;
    static final int RESULT_GIVE_UP_MS = 15000;
    static final long SAVE_MS = 4L * 24 * 60 * 60 * 1000;

    static final String[] FILTERS = {"All", "Overdue", "Next 7 days", "Next 30 days", "Next 3 months", "Next 6 months"};
    static final int[] FILTER_DAYS = {0, 0, 7, 30, 90, 180};
    static final String[] USER_MENU = {"Profile", "Grades", "Calendar", "Private files", "Reports", "Preferences", "Log out"};
    static final String[] USER_PATHS = {"/user/profile.php", "/grade/report/overview/index.php",
            "/calendar/view.php?view=month", "/user/files.php", "/reportbuilder/index.php",
            "/user/preferences.php", null};

    static final String[][] LINKS = {
            {"CSU Official Website", "https://www.carsu.edu.ph/"},
            {"MyAdmission", "https://myadmission.carsu.edu.ph/"},
            {"MySchool", "https://myschool.carsu.edu.ph/"},
            {"MyLibrary", "https://herolearningcommons.vercel.app/"},
            {"Call 0917 707 8764", "tel:+639177078764"},
            {"pico.csu@carsu.edu.ph", "mailto:pico.csu@carsu.edu.ph"}};

    private WebView webView;
    private LinearLayout layoutMasaoLogin, llDashboard, llMyCourses, llTimeline, llRecentCourses, llRecentItems, llUpcoming;
    private LinearLayout llHome, llHomeSite, llHomePosts, llHomeNews, llHomeUpcoming, llHomeLinks;
    private LinearLayout llCourseView, llCourseSections, llProfileView, llProfileCategories;
    private MaterialButton btnCourseBack, btnProfileBack;
    private TextView tvCourseViewTitle, tvProfileAvatar, tvProfileName;
    private View cardHomeSite, cardHomePosts, cardHomeNews;
    private NestedScrollView scrollHome;
    private TextInputEditText etMasaoUser, etMasaoPass;
    private MaterialButton btnMasaoLogin, btnTlFilter, btnTlSort;
    private TextView tvMasaoStatus, tvState, tvNoCourses, tvTlEmpty, tvRcEmpty, tvRiEmpty, tvUpEmpty, tvHomeUpEmpty;
    private TextView tvBell, tvMsg, tvAvatar, tvPrivateFiles;
    private EditText etTlSearch;
    private ChipGroup chipCourses;
    private TabLayout tabs;
    private CheckBox cbSave;
    private SharedPreferences prefs;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean done, homeStarted, autoTried, frontLoaded;
    private String pendingU, pendingP, sesskey = "", currentCourseUrl = "";

    // timeline state
    private int filterIdx = 0;
    private boolean sortByCourse = false;

    // data
    private final List<List<Course>> courseSets = new ArrayList<>();
    private final List<Course> shownCourses = new ArrayList<>();
    private final List<Course> recentCourses = new ArrayList<>();
    private final List<Event> timeline = new ArrayList<>();
    private final List<Event> recentItems = new ArrayList<>();
    private final List<Event> upcoming = new ArrayList<>();
    private CourseAdapter courseAdapter;

    private final SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat fullFmt = new SimpleDateFormat("EEE, MMM d \u00b7 h:mm a", Locale.getDefault());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_masao_lms);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("masaoLMS");
        }

        webView = findViewById(R.id.webViewMasao);
        layoutMasaoLogin = findViewById(R.id.layoutMasaoLogin);
        scrollHome = findViewById(R.id.scrollMasaoHome);
        etMasaoUser = findViewById(R.id.etMasaoUser);
        etMasaoPass = findViewById(R.id.etMasaoPass);
        btnMasaoLogin = findViewById(R.id.btnMasaoLogin);
        tvMasaoStatus = findViewById(R.id.tvMasaoStatus);
        cbSave = findViewById(R.id.cbMasaoSave);

        tvState = findViewById(R.id.tvMasaoState);
        tvNoCourses = findViewById(R.id.tvMasaoNoCourses);
        chipCourses = findViewById(R.id.chipMasaoCourses);
        tabs = findViewById(R.id.tabMasao);
        llDashboard = findViewById(R.id.llDashboard);
        llMyCourses = findViewById(R.id.llMyCourses);
        llTimeline = findViewById(R.id.llTimeline);
        llRecentCourses = findViewById(R.id.llRecentCourses);
        llRecentItems = findViewById(R.id.llRecentItems);
        llUpcoming = findViewById(R.id.llUpcoming);
        tvTlEmpty = findViewById(R.id.tvTlEmpty);
        tvRcEmpty = findViewById(R.id.tvRcEmpty);
        tvRiEmpty = findViewById(R.id.tvRiEmpty);
        tvUpEmpty = findViewById(R.id.tvUpEmpty);
        tvBell = findViewById(R.id.tvBell);
        tvMsg = findViewById(R.id.tvMsg);
        tvAvatar = findViewById(R.id.tvAvatar);
        tvPrivateFiles = findViewById(R.id.tvPrivateFiles);
        btnTlFilter = findViewById(R.id.btnTlFilter);
        btnTlSort = findViewById(R.id.btnTlSort);
        etTlSearch = findViewById(R.id.etTlSearch);

        llHome = findViewById(R.id.llHome);
        llHomeSite = findViewById(R.id.llHomeSite);
        llHomePosts = findViewById(R.id.llHomePosts);
        llHomeNews = findViewById(R.id.llHomeNews);
        llHomeUpcoming = findViewById(R.id.llHomeUpcoming);
        llHomeLinks = findViewById(R.id.llHomeLinks);
        cardHomeSite = findViewById(R.id.cardHomeSite);
        cardHomePosts = findViewById(R.id.cardHomePosts);
        cardHomeNews = findViewById(R.id.cardHomeNews);
        tvHomeUpEmpty = findViewById(R.id.tvHomeUpEmpty);

        llCourseView = findViewById(R.id.llCourseView);
        llCourseSections = findViewById(R.id.llCourseSections);
        btnCourseBack = findViewById(R.id.btnCourseBack);
        tvCourseViewTitle = findViewById(R.id.tvCourseViewTitle);
        btnCourseBack.setOnClickListener(v -> hideCourseView());

        llProfileView = findViewById(R.id.llProfileView);
        llProfileCategories = findViewById(R.id.llProfileCategories);
        btnProfileBack = findViewById(R.id.btnProfileBack);
        tvProfileAvatar = findViewById(R.id.tvProfileAvatar);
        tvProfileName = findViewById(R.id.tvProfileName);
        btnProfileBack.setOnClickListener(v -> hideProfileView());

        // encrypted storage for "save account"
        try {
            MasterKey mk = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            prefs = EncryptedSharedPreferences.create(this, "masao_creds", mk,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) { prefs = null; }

        for (int i = 0; i < 3; i++) courseSets.add(new ArrayList<>());

        // My courses tab
        courseAdapter = new CourseAdapter(shownCourses, this::openCourse);
        RecyclerView rvCourses = findViewById(R.id.rvMasaoCourses);
        rvCourses.setLayoutManager(new LinearLayoutManager(this));
        rvCourses.setNestedScrollingEnabled(false);
        rvCourses.setAdapter(courseAdapter);
        ListControlsKt.setOptions(chipCourses, Arrays.asList("In progress", "Upcoming", "Past"), i -> {
            showCourses(i);
            return Unit.INSTANCE;
        });

        // tabs: Home (native), Dashboard (native), My courses (native)
        tabs.addTab(tabs.newTab().setText("Home"), false);
        tabs.addTab(tabs.newTab().setText("Dashboard"), true);
        tabs.addTab(tabs.newTab().setText("My courses"), false);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) { onTab(t.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab t) { }
            @Override public void onTabReselected(TabLayout.Tab t) { onTab(t.getPosition()); }
        });

        // timeline controls
        btnTlFilter.setOnClickListener(v -> {
            PopupMenu m = new PopupMenu(this, v);
            for (int i = 0; i < FILTERS.length; i++) m.getMenu().add(0, i, i, FILTERS[i]);
            m.setOnMenuItemClickListener(it -> {
                filterIdx = it.getItemId();
                btnTlFilter.setText(FILTERS[filterIdx] + " \u25BE");
                renderTimeline();
                return true;
            });
            m.show();
        });
        btnTlSort.setOnClickListener(v -> {
            PopupMenu m = new PopupMenu(this, v);
            m.getMenu().add(0, 0, 0, "Sort by dates");
            m.getMenu().add(0, 1, 1, "Sort by courses");
            m.setOnMenuItemClickListener(it -> {
                sortByCourse = it.getItemId() == 1;
                btnTlSort.setText((sortByCourse ? "Sort by courses" : "Sort by dates") + " \u25BE");
                renderTimeline();
                return true;
            });
            m.show();
        });
        etTlSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { renderTimeline(); }
        });

        // top bar
        tvBell.setOnClickListener(v -> openInWeb(BASE_URL + "/message/output/popup/notifications.php"));
        tvMsg.setOnClickListener(v -> openInWeb(BASE_URL + "/message/index.php"));
        tvPrivateFiles.setOnClickListener(v -> openInWeb(BASE_URL + "/user/files.php"));
        tvAvatar.setOnClickListener(v -> {
            PopupMenu m = new PopupMenu(this, v);
            for (int i = 0; i < USER_MENU.length; i++) m.getMenu().add(0, i, i, USER_MENU[i]);
            m.setOnMenuItemClickListener(it -> {
                int idx = it.getItemId();
                if (idx == 0) {
                    loadProfileView();
                } else {
                    String path = USER_PATHS[idx];
                    if (path == null) logout(); else openInWeb(BASE_URL + path);
                }
                return true;
            });
            m.show();
        });

        tvState.setOnClickListener(v -> {
            if (!homeStarted) return;
            if (llProfileView.getVisibility() == View.VISIBLE) {
                loadProfileView();
            } else if (llCourseView.getVisibility() == View.VISIBLE && !currentCourseUrl.isEmpty()) {
                loadCourseView(currentCourseUrl, tvCourseViewTitle.getText().toString());
            } else if (tabs.getSelectedTabPosition() == 0) {
                loadFront();
            } else {
                loadHome();
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(i);
            } catch (Exception e) {
                showError("No app found to open this file.");
            }
        });

        btnMasaoLogin.setEnabled(false);
        btnMasaoLogin.setOnClickListener(v -> startLogin());

        webView.setWebViewClient(new WebViewClient() {
            @Override public void doUpdateVisitedHistory(WebView v, String url, boolean reload) { checkUrl(url); }
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) { checkUrl(url); }
            @Override public void onPageFinished(WebView v, String url) {
                checkUrl(url);
                if (url != null && url.contains("login") && !done) {
                    btnMasaoLogin.setEnabled(true);
                    if (tvMasaoStatus != null) tvMasaoStatus.setText("");
                    tryAutoLogin();
                }
                if (done && !homeStarted && url != null && !url.contains("login")) {
                    homeStarted = true;
                    loadHome();
                }
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (llProfileView.getVisibility() == View.VISIBLE) {
                    hideProfileView();
                } else if (llCourseView.getVisibility() == View.VISIBLE) {
                    hideCourseView();
                } else if (done && scrollHome.getVisibility() != View.VISIBLE) {
                    showHome();
                } else {
                    setEnabled(false);
                    finish();
                }
            }
        });

        // always start from a clean login page (self-heals stale sessions)
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(ok -> {
            cm.flush();
            runOnUiThread(() -> {
                webView.clearCache(true);
                webView.loadUrl(LOGIN_URL);
            });
        });
    }

    // ---------- tabs ----------

    private void onTab(int pos) {
        llHome.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
        llDashboard.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
        llMyCourses.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
        llCourseView.setVisibility(View.GONE);
        llProfileView.setVisibility(View.GONE);
        if (pos == 0 && !frontLoaded && done && homeStarted) loadFront();
    }

    // ---------- saved account ----------

    private void saveCreds(String u, String p) {
        if (prefs == null) return;
        prefs.edit().putString("u", u).putString("p", p)
                .putLong("t", System.currentTimeMillis()).apply();
    }

    private void clearCreds() {
        if (prefs != null) prefs.edit().clear().apply();
    }

    private String[] validCreds() {
        if (prefs == null) return null;
        String u = prefs.getString("u", null), p = prefs.getString("p", null);
        long t = prefs.getLong("t", 0);
        if (u == null || p == null) return null;
        if (System.currentTimeMillis() - t > SAVE_MS) { clearCreds(); return null; }
        return new String[]{u, p};
    }

    private void tryAutoLogin() {
        if (autoTried || done) return;
        String[] c = validCreds();
        if (c == null) return;
        autoTried = true;
        cbSave.setChecked(true);
        etMasaoUser.setText(c[0]);
        etMasaoPass.setText(c[1]);
        startLogin();
    }

    // ---------- login ----------

    private void checkUrl(String url) {
        if (done || url == null) return;
        if (url.contains(DASHBOARD_URL) || url.contains("user/profile.php")) {
            done = true;
            h.removeCallbacksAndMessages(null);
            if (pendingU != null) {
                if (cbSave.isChecked()) saveCreds(pendingU, pendingP); else clearCreds();
                pendingU = pendingP = null;
            }
            runOnUiThread(() -> {
                scrollHome.setVisibility(View.VISIBLE);
                layoutMasaoLogin.animate().alpha(0f).setDuration(200)
                        .withEndAction(() -> layoutMasaoLogin.setVisibility(View.GONE));
            });
        }
    }

    private void startLogin() {
        String u = etMasaoUser != null && etMasaoUser.getText() != null ? String.valueOf(etMasaoUser.getText()).trim() : "";
        String p = etMasaoPass != null && etMasaoPass.getText() != null ? String.valueOf(etMasaoPass.getText()) : "";
        if (u.isEmpty() || p.isEmpty()) {
            if (tvMasaoStatus != null) tvMasaoStatus.setText("Enter your username and password.");
            return;
        }
        pendingU = u; pendingP = p;
        btnMasaoLogin.setEnabled(false);
        if (tvMasaoStatus != null) tvMasaoStatus.setText("Signing in to masaoLMS\u2026");

        String js = "(function(u,p){"
                + "var a = document.getElementById('username'), b = document.getElementById('password');"
                + "if(!a || !b) return 'nofields';"
                + "a.value = u; a.dispatchEvent(new Event('input', {bubbles:true})); a.dispatchEvent(new Event('change', {bubbles:true}));"
                + "b.value = p; b.dispatchEvent(new Event('input', {bubbles:true})); b.dispatchEvent(new Event('change', {bubbles:true}));"
                + "var f = document.getElementById('login');"
                + "if(f) { f.submit(); return 'ok'; }"
                + "var btn = document.getElementById('loginbtn');"
                + "if(btn) { btn.click(); return 'ok'; }"
                + "return 'nobtn';})(" + JSONObject.quote(u) + "," + JSONObject.quote(p) + ")";

        webView.evaluateJavascript(js, r -> {
            if (r != null && r.contains("nofields")) {
                CookieManager cm = CookieManager.getInstance();
                cm.removeAllCookies(ok -> {
                    cm.flush();
                    runOnUiThread(() -> {
                        if (tvMasaoStatus != null) tvMasaoStatus.setText("Session reset. Tap Login again.");
                        btnMasaoLogin.setEnabled(false);
                        webView.loadUrl(LOGIN_URL);
                    });
                });
                return;
            }
            if (r == null || !r.contains("ok")) {
                if (tvMasaoStatus != null) tvMasaoStatus.setText("Could not submit login form. Please try again.");
                btnMasaoLogin.setEnabled(true);
                return;
            }
            h.postDelayed(() -> {
                if (!done) {
                    webView.evaluateJavascript("(function(){ var err = document.querySelector('.loginerrors, .alert-danger, .error'); return err ? err.innerText : 'none'; })()", errVal -> {
                        if (!done) {
                            clearCreds();
                            String errText = errVal != null ? errVal.replaceAll("[\"\\\\]", "").trim() : "";
                            if (!errText.isEmpty() && !errText.equals("none")) {
                                if (tvMasaoStatus != null) tvMasaoStatus.setText(errText);
                            } else {
                                if (tvMasaoStatus != null) tvMasaoStatus.setText("Login failed. Check your credentials.");
                            }
                            btnMasaoLogin.setEnabled(true);
                        }
                    });
                }
            }, 5000);
        });
    }

    private void logout() {
        clearCreds();
        done = false; homeStarted = false; autoTried = true; frontLoaded = false; sesskey = "";
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(ok -> {
            cm.flush();
            runOnUiThread(() -> {
                llProfileView.setVisibility(View.GONE);
                llCourseView.setVisibility(View.GONE);
                scrollHome.setVisibility(View.GONE);
                layoutMasaoLogin.setAlpha(1f);
                layoutMasaoLogin.setVisibility(View.VISIBLE);
                if (etMasaoPass.getText() != null) etMasaoPass.getText().clear();
                btnMasaoLogin.setEnabled(false);
                tvMasaoStatus.setText("");
                webView.loadUrl(LOGIN_URL);
            });
        });
    }

    // ---------- User Profile View ----------

    private void hideProfileView() {
        llProfileView.setVisibility(View.GONE);
        int pos = tabs.getSelectedTabPosition();
        onTab(pos);
    }

    private void loadProfileView() {
        llHome.setVisibility(View.GONE);
        llDashboard.setVisibility(View.GONE);
        llMyCourses.setVisibility(View.GONE);
        llCourseView.setVisibility(View.GONE);
        llProfileView.setVisibility(View.VISIBLE);

        tvState.setVisibility(View.VISIBLE);
        tvState.setText("Loading profile\u2026");

        String js = "window.__masaoProfile=null;"
            + "fetch('/user/profile.php',{credentials:'same-origin'})"
            + ".then(function(r){return r.text();})"
            + ".then(function(t){"
            + " var d=new DOMParser().parseFromString(t,'text/html');"
            + " var nameEl=d.querySelector('#page-header h1, h1.h2, .page-header-headings h1');"
            + " var name=nameEl?nameEl.textContent.trim():'';"
            + " var categories=[];"
            + " d.querySelectorAll('.profile_tree section.node_category, .profile_tree .node_category').forEach(function(cat){"
            + "  var titleEl=cat.querySelector('h3');"
            + "  var title=titleEl?titleEl.textContent.trim():'';"
            + "  var fields=[];"
            + "  cat.querySelectorAll('li').forEach(function(li){"
            + "   var dt=li.querySelector('dt'); var dd=li.querySelector('dd'); var a=li.querySelector('a');"
            + "   if(dt && dd){"
            + "    fields.push({label:dt.textContent.trim(),value:dd.textContent.replace(/\\s+/g,' ').trim()});"
            + "   } else if(a){"
            + "    fields.push({label:a.textContent.trim(),value:'',url:a.href});"
            + "   } else if(li.textContent.trim()){"
            + "    fields.push({label:li.textContent.trim(),value:''});"
            + "   }"
            + "  });"
            + "  if(fields.length>0 || title){"
            + "   categories.push({title:title,fields:fields});"
            + "  }"
            + " });"
            + " window.__masaoProfile=JSON.stringify({name:name,categories:categories});"
            + "})"
            + ".catch(function(e){window.__masaoProfile='ERR';});";

        webView.evaluateJavascript(js, null);
        pollProfileView(0);
    }

    private void pollProfileView(int waited) {
        h.postDelayed(() -> {
            if (isFinishing() || webView == null) return;
            webView.evaluateJavascript("window.__masaoProfile", v -> {
                String raw = unquote(v);
                if (!raw.isEmpty()) { renderProfileView(raw); return; }
                if (waited >= RESULT_GIVE_UP_MS) {
                    showError("Couldn't load profile. Tap to retry.");
                    return;
                }
                pollProfileView(waited + RESULT_POLL_MS);
            });
        }, RESULT_POLL_MS);
    }

    private void renderProfileView(String raw) {
        if (raw.equals("ERR")) {
            showError("Couldn't load profile. Tap to retry.");
            return;
        }
        try {
            JSONObject obj = new JSONObject(raw);
            tvState.setVisibility(View.GONE);
            String name = obj.optString("name", "");
            if (!name.isEmpty()) {
                tvProfileName.setText(name);
                StringBuilder sb = new StringBuilder();
                for (String w : name.trim().split("\\s+"))
                    if (!w.isEmpty() && sb.length() < 2) sb.append(Character.toUpperCase(w.charAt(0)));
                tvProfileAvatar.setText(sb.length() == 0 ? "?" : sb.toString());
            }

            llProfileCategories.removeAllViews();
            JSONArray categories = obj.optJSONArray("categories");
            if (categories == null || categories.length() == 0) {
                showError("No profile details found.");
                return;
            }

            LayoutInflater inf = getLayoutInflater();
            for (int i = 0; i < categories.length(); i++) {
                JSONObject cat = categories.getJSONObject(i);
                String title = cat.optString("title", "");
                JSONArray fields = cat.optJSONArray("fields");

                View card = inf.inflate(R.layout.item_masao_post, llProfileCategories, false);
                ((TextView) card.findViewById(R.id.tvPostSubject)).setText(title);
                card.findViewById(R.id.tvPostMeta).setVisibility(View.GONE);
                card.findViewById(R.id.tvPostReplies).setVisibility(View.GONE);
                card.findViewById(R.id.tvPostBody).setVisibility(View.GONE);

                LinearLayout fieldContainer = new LinearLayout(this);
                fieldContainer.setOrientation(LinearLayout.VERTICAL);
                int p = (int) (8 * getResources().getDisplayMetrics().density);
                fieldContainer.setPadding(0, p, 0, 0);

                if (fields != null) {
                    for (int j = 0; j < fields.length(); j++) {
                        JSONObject f = fields.getJSONObject(j);
                        String label = f.optString("label", "");
                        String val = f.optString("value", "");
                        String url = f.optString("url", "");

                        Event e = new Event(new JSONObject().put("name", label).put("url", url));
                        View row = rowView(fieldContainer, e, val, false);
                        if (!url.isEmpty()) {
                            row.setOnClickListener(v -> openCourse(url, label));
                        }
                        fieldContainer.addView(row);
                    }
                }

                ((ViewGroup) card).addView(fieldContainer);
                llProfileCategories.addView(card);
            }
        } catch (Exception e) {
            showError("Couldn't read profile details. Tap to retry.");
        }
    }

    // ---------- Course View ----------

    private void openCourse(String url, String title) {
        if (url == null || url.isEmpty()) return;
        if (url.contains("/course/view.php") || url.contains("/user/view.php")) {
            loadCourseView(url, title);
        } else if (url.contains("/user/profile.php")) {
            loadProfileView();
        } else {
            openInWeb(url);
        }
    }

    private void hideCourseView() {
        llCourseView.setVisibility(View.GONE);
        int pos = tabs.getSelectedTabPosition();
        onTab(pos);
    }

    private void toggleCompletion(String cmid, boolean markDone, Runnable onSuccess) {
        if (sesskey.isEmpty() || cmid == null || cmid.isEmpty()) return;
        try {
            int id = Integer.parseInt(cmid);
            JSONArray req = new JSONArray();
            req.put(new JSONObject()
                    .put("index", 0)
                    .put("methodname", "core_courseformat_update_course")
                    .put("args", new JSONObject()
                            .put("action", "cm_completion")
                            .put("courseid", 0)
                            .put("ids", new JSONArray().put(id))
                            .put("completed", markDone ? 1 : 0)
                    ));
            String js = "fetch('/lib/ajax/service.php?sesskey=" + sesskey + "&info=core_courseformat_update_course',"
                    + "{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json'},"
                    + "body:" + JSONObject.quote(req.toString()) + "});";
            webView.evaluateJavascript(js, null);
            if (onSuccess != null) onSuccess.run();
        } catch (Exception ignored) { }
    }

    private void loadCourseView(String url, String title) {
        currentCourseUrl = url;
        llHome.setVisibility(View.GONE);
        llDashboard.setVisibility(View.GONE);
        llMyCourses.setVisibility(View.GONE);
        llProfileView.setVisibility(View.GONE);
        llCourseView.setVisibility(View.VISIBLE);
        if (title != null && !title.isEmpty()) tvCourseViewTitle.setText(title);
        tvState.setVisibility(View.VISIBLE);
        tvState.setText("Loading course contents\u2026");

        String courseId = "";
        try {
            Uri u = Uri.parse(url);
            courseId = u.getQueryParameter("id");
            if (courseId == null) courseId = u.getQueryParameter("course");
        } catch (Exception ignored) { }
        if (courseId == null || courseId.isEmpty()) courseId = "0";

        String fetchUrl = url.contains("/user/view.php") ? url : "/course/view.php?id=" + courseId;

        String js = "window.__masaoCourse=null;"
            + "fetch(" + JSONObject.quote(fetchUrl) + ",{credentials:'same-origin'})"
            + ".then(function(r){return r.text();})"
            + ".then(function(t){"
            + " var d=new DOMParser().parseFromString(t,'text/html');"
            + " var h1=d.querySelector('#page-header h1, h1.h2, .page-header-headings h1');"
            + " var title=h1?h1.textContent.trim():'';"
            + " var sections=[];"
            + " d.querySelectorAll('ul.topics > li.section, li.course-section').forEach(function(sec){"
            + "  var secNameEl=sec.querySelector('.sectionname, [data-for=\"section_title\"]');"
            + "  var secName=secNameEl?secNameEl.textContent.replace(/Collapse all|Expand all|Select section/gi,'').trim():'';"
            + "  if(!secName) return;"
            + "  var items=[];"
            + "  sec.querySelectorAll('li.activity').forEach(function(act){"
            + "   var a=act.querySelector('a.aalink'); if(!a) return;"
            + "   var nameEl=act.querySelector('.instancename');"
            + "   var name=nameEl?nameEl.textContent.replace(/\\s+(Forum|URL|File)$/gi,'').trim():a.textContent.trim();"
            + "   var detailsEl=act.querySelector('.resourcelinkdetails');"
            + "   var details=detailsEl?detailsEl.textContent.trim():'';"
            + "   var typeMatch=act.className.match(/modtype_(\\w+)/);"
            + "   var type=typeMatch?typeMatch[1]:'';"
            + "   var cmidAttr=act.getAttribute('data-id')||(act.id?act.id.replace('module-',''):'');"
            + "   var cmBtn=act.querySelector('[data-action=\"toggle-manual-completion\"], [data-cmid]');"
            + "   var cmid=cmBtn?cmBtn.getAttribute('data-cmid'):cmidAttr;"
            + "   var cmDone=cmBtn?(cmBtn.getAttribute('data-toggletype')==='manual:undo'||cmBtn.classList.contains('btn-success')):act.querySelector('.text-success,.btn-success')!==null;"
            + "   var cmManual=cmBtn!==null;"
            + "   items.push({name:name,url:a.href,type:type,details:details,cmid:cmid,cmDone:cmDone,cmManual:cmManual});"
            + "  });"
            + "  if(items.length>0 || secName){"
            + "   sections.push({name:secName,items:items});"
            + "  }"
            + " });"
            + " window.__masaoCourse=JSON.stringify({title:title,sections:sections});"
            + "})"
            + ".catch(function(e){window.__masaoCourse='ERR';});";

        webView.evaluateJavascript(js, null);
        pollCourseView(0);
    }

    private void pollCourseView(int waited) {
        h.postDelayed(() -> {
            if (isFinishing() || webView == null) return;
            webView.evaluateJavascript("window.__masaoCourse", v -> {
                String raw = unquote(v);
                if (!raw.isEmpty()) { renderCourseView(raw); return; }
                if (waited >= RESULT_GIVE_UP_MS) {
                    showError("Couldn't load course. Tap to retry.");
                    return;
                }
                pollCourseView(waited + RESULT_POLL_MS);
            });
        }, RESULT_POLL_MS);
    }

    private void renderCourseView(String raw) {
        if (raw.equals("ERR")) {
            showError("Couldn't load course. Tap to retry.");
            return;
        }
        try {
            JSONObject obj = new JSONObject(raw);
            tvState.setVisibility(View.GONE);
            String title = obj.optString("title", "");
            if (!title.isEmpty()) tvCourseViewTitle.setText(title);

            llCourseSections.removeAllViews();
            JSONArray sections = obj.optJSONArray("sections");
            if (sections == null || sections.length() == 0) {
                showError("No course topics found.");
                return;
            }

            LayoutInflater inf = getLayoutInflater();
            for (int i = 0; i < sections.length(); i++) {
                JSONObject sec = sections.getJSONObject(i);
                String secName = sec.optString("name", "");
                JSONArray items = sec.optJSONArray("items");

                View card = inf.inflate(R.layout.item_masao_post, llCourseSections, false);
                ((TextView) card.findViewById(R.id.tvPostSubject)).setText(secName);
                card.findViewById(R.id.tvPostMeta).setVisibility(View.GONE);
                card.findViewById(R.id.tvPostReplies).setVisibility(View.GONE);

                TextView body = card.findViewById(R.id.tvPostBody);
                body.setVisibility(View.GONE);

                LinearLayout itemContainer = new LinearLayout(this);
                itemContainer.setOrientation(LinearLayout.VERTICAL);
                int p = (int) (8 * getResources().getDisplayMetrics().density);
                itemContainer.setPadding(0, p, 0, 0);

                if (items != null) {
                    for (int j = 0; j < items.length(); j++) {
                        JSONObject item = items.getJSONObject(j);
                        String itemName = item.optString("name", "");
                        String itemUrl = item.optString("url", "");
                        String type = item.optString("type", "");
                        String details = item.optString("details", "");
                        boolean cmManual = item.optBoolean("cmManual", false);
                        boolean cmDone = item.optBoolean("cmDone", false);
                        String cmid = item.optString("cmid", "");

                        Event e = new Event(new JSONObject()
                                .put("name", itemName).put("url", itemUrl));
                        String when = details;
                        if (cmManual && !cmid.isEmpty()) {
                            when = (cmDone ? "\u2713 Done" : "To do") + (when.isEmpty() ? "" : " \u00b7 " + when);
                        } else {
                            switch (type) {
                                case "forum": when = "Forum" + (when.isEmpty() ? "" : " \u00b7 " + when); break;
                                case "url": when = "Link / Video" + (when.isEmpty() ? "" : " \u00b7 " + when); break;
                                case "resource": when = "File" + (when.isEmpty() ? "" : " \u00b7 " + when); break;
                            }
                        }

                        View row = rowView(itemContainer, e, when, false);
                        if (cmManual && !cmid.isEmpty()) {
                            boolean finalDone = cmDone;
                            row.setOnLongClickListener(v -> {
                                toggleCompletion(cmid, !finalDone, () -> loadCourseView(currentCourseUrl, tvCourseViewTitle.getText().toString()));
                                return true;
                            });
                        }
                        itemContainer.addView(row);
                    }
                }

                ((ViewGroup) card).addView(itemContainer);
                llCourseSections.addView(card);
            }
        } catch (Exception e) {
            showError("Couldn't read course content. Tap to retry.");
        }
    }

    // ---------- Home (site front page) ----------

    private void loadFront() {
        frontLoaded = true;
        tvState.setVisibility(View.VISIBLE);
        tvState.setText("Loading\u2026");
        String js = "window.__masaoFront=null;"
            + "fetch('/?redirect=0',{credentials:'same-origin'})"
            + ".then(function(r){return r.text();})"
            + ".then(function(t){"
            + " var d=new DOMParser().parseFromString(t,'text/html');"
            + " function tx(e){return e?e.textContent.replace(/\\s+/g,' ').trim():'';}"
            + " var o={mods:[],posts:[],news:[]};"
            + " d.querySelectorAll('.sitetopic li.activity').forEach(function(li){"
            + "  var a=li.querySelector('a.aalink'); if(!a) return;"
            + "  o.mods.push({name:tx(li.querySelector('.instancename')).replace(/\\s+Page$/,''),url:a.href});});"
            + " d.querySelectorAll('#site-news-forum article.forum-post-container').forEach(function(p){"
            + "  var tm=p.querySelector('time'), l=p.querySelector('.link a'), lk=p.querySelector('.link');"
            + "  var m=tx(lk).match(/\\((\\d+) repl/);"
            + "  o.posts.push({subject:tx(p.querySelector('[data-region-content=\"forum-post-core-subject\"]')),"
            + "   by:tx(p.querySelector('header a')),"
            + "   time:tm?Math.floor(new Date(tm.getAttribute('datetime')).getTime()/1000):0,"
            + "   body:tx(p.querySelector('.post-content-container')).substring(0,300),"
            + "   url:l?l.href:'',replies:m?parseInt(m[1]):-1});});"
            + " d.querySelectorAll('.block_news_items li.post').forEach(function(li){"
            + "  var a=li.querySelector('.info a');"
            + "  o.news.push({date:tx(li.querySelector('.date')),by:tx(li.querySelector('.name')),"
            + "   title:tx(a),url:a?a.href:''});});"
            + " window.__masaoFront=JSON.stringify(o);})"
            + ".catch(function(e){window.__masaoFront='ERR';});";
        webView.evaluateJavascript(js, null);
        pollFront(0);
    }

    private void pollFront(int waited) {
        h.postDelayed(() -> {
            if (isFinishing() || webView == null) return;
            webView.evaluateJavascript("window.__masaoFront", v -> {
                String raw = unquote(v);
                if (!raw.isEmpty()) { renderFront(raw); return; }
                if (waited >= RESULT_GIVE_UP_MS) { failFront(); return; }
                pollFront(waited + RESULT_POLL_MS);
            });
        }, RESULT_POLL_MS);
    }

    private void failFront() {
        frontLoaded = false;
        showError("Couldn't load Home. Tap to retry.");
    }

    private void renderFront(String raw) {
        if (raw.equals("ERR")) { failFront(); return; }
        try {
            JSONObject o = new JSONObject(raw);
            tvState.setVisibility(View.GONE);

            // Site pages
            JSONArray mods = o.optJSONArray("mods");
            llHomeSite.removeAllViews();
            cardHomeSite.setVisibility(mods != null && mods.length() > 0 ? View.VISIBLE : View.GONE);
            for (int i = 0; mods != null && i < mods.length(); i++) {
                JSONObject m = mods.getJSONObject(i);
                Event e = new Event(new JSONObject()
                        .put("name", m.optString("name")).put("url", m.optString("url")));
                llHomeSite.addView(rowView(llHomeSite, e, "Tap to open", false));
            }

            // Announcements, News & Updates
            JSONArray posts = o.optJSONArray("posts");
            llHomePosts.removeAllViews();
            cardHomePosts.setVisibility(posts != null && posts.length() > 0 ? View.VISIBLE : View.GONE);
            for (int i = 0; posts != null && i < posts.length(); i++) {
                JSONObject p = posts.getJSONObject(i);
                View v = getLayoutInflater().inflate(R.layout.item_masao_post, llHomePosts, false);
                ((TextView) v.findViewById(R.id.tvPostSubject)).setText(p.optString("subject"));
                long t = p.optLong("time");
                String meta = p.optString("by");
                if (t > 0) meta += (meta.isEmpty() ? "" : " \u00b7 ") + fullFmt.format(new Date(t * 1000));
                ((TextView) v.findViewById(R.id.tvPostMeta)).setText(meta);
                TextView body = v.findViewById(R.id.tvPostBody);
                body.setText(p.optString("body"));
                body.setVisibility(p.optString("body").isEmpty() ? View.GONE : View.VISIBLE);
                TextView rep = v.findViewById(R.id.tvPostReplies);
                int r = p.optInt("replies", -1);
                rep.setVisibility(r >= 0 ? View.VISIBLE : View.GONE);
                rep.setText(r + (r == 1 ? " reply" : " replies"));
                String url = p.optString("url");
                if (!url.isEmpty()) v.setOnClickListener(x -> openInWeb(url));
                llHomePosts.addView(v);
            }

            // Latest announcements block
            JSONArray news = o.optJSONArray("news");
            llHomeNews.removeAllViews();
            cardHomeNews.setVisibility(news != null && news.length() > 0 ? View.VISIBLE : View.GONE);
            for (int i = 0; news != null && i < news.length(); i++) {
                JSONObject n = news.getJSONObject(i);
                Event e = new Event(new JSONObject()
                        .put("name", n.optString("title")).put("coursename", n.optString("by"))
                        .put("url", n.optString("url")));
                llHomeNews.addView(rowView(llHomeNews, e, n.optString("date"), true));
            }

            // Upcoming events (from dashboard API data)
            fillRows(llHomeUpcoming, tvHomeUpEmpty, upcoming, true);

            // CSU links
            if (llHomeLinks.getChildCount() == 0) {
                for (String[] l : LINKS) {
                    View v = getLayoutInflater().inflate(R.layout.item_menu_option, llHomeLinks, false);
                    ((TextView) v.findViewById(R.id.tvMenuTitle)).setText(l[0]);
                    ((ImageView) v.findViewById(R.id.imgMenuIcon)).setImageResource(R.drawable.ic_curriculum);
                    v.setOnClickListener(x -> {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(l[1]))); }
                        catch (Exception ignored) { }
                    });
                    llHomeLinks.addView(v);
                }
            }
        } catch (Exception e) {
            failFront();
        }
    }

    // ---------- native data ----------

    private static String unquote(String v) {
        if (v == null || v.equals("null")) return "";
        try { return new JSONArray("[" + v + "]").optString(0, ""); }
        catch (Exception e) { return ""; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static JSONObject call(int idx, String method, JSONObject args) throws Exception {
        return new JSONObject().put("index", idx).put("methodname", method).put("args", args);
    }

    private void loadHome() {
        tvState.setVisibility(View.VISIBLE);
        tvState.setText("Loading\u2026");
        webView.evaluateJavascript(
                "(function(){try{var u=document.querySelector('.usermenu .userinitials');"
                        + "return M.cfg.sesskey+'|'+M.cfg.userId+'|'+(u?u.getAttribute('title'):'');}catch(e){return '';}})()",
                v -> {
                    String[] p = unquote(v).split("\\|", 3);
                    if (p.length < 2 || p[0].isEmpty()) { showError("Couldn't read your session. Tap to retry."); return; }
                    sesskey = p[0];
                    int uid = parseInt(p[1]);
                    if (p.length == 3) setAvatar(p[2]);
                    requestData(uid);
                });
    }

    private void requestData(int uid) {
        try {
            long now = System.currentTimeMillis() / 1000;
            JSONArray req = new JSONArray();
            String[] classes = {"inprogress", "future", "past"};
            for (int i = 0; i < 3; i++) {
                req.put(call(i, "core_course_get_enrolled_courses_by_timeline_classification",
                        new JSONObject().put("classification", classes[i]).put("limit", 0)
                                .put("offset", 0).put("sort", "fullname")));
            }
            req.put(call(3, "core_calendar_get_action_events_by_timesort",
                    new JSONObject().put("limitnum", 100).put("timesortfrom", now - 14L * 86400)
                            .put("limittononsuspendedevents", true)));
            req.put(call(4, "core_course_get_recent_courses",
                    new JSONObject().put("userid", uid).put("limit", 10).put("offset", 0)
                            .put("sort", "ul.timeaccess desc")));
            req.put(call(5, "block_recentlyaccesseditems_get_recent_items",
                    new JSONObject().put("limit", 9)));
            req.put(call(6, "core_calendar_get_calendar_upcoming_view",
                    new JSONObject().put("courseid", 1).put("categoryid", 0)));
            req.put(call(7, "message_popup_get_unread_notification_count",
                    new JSONObject().put("useridto", uid)));
            req.put(call(8, "core_message_get_unread_conversation_counts",
                    new JSONObject().put("useridto", uid)));

            String js = "window.__masao=null;"
                    + "fetch(" + JSONObject.quote(AJAX_PATH + "?sesskey=" + sesskey + "&info=masao") + ","
                    + "{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json'},"
                    + "body:" + JSONObject.quote(req.toString()) + "})"
                    + ".then(function(r){return r.text();})"
                    + ".then(function(t){window.__masao=t;})"
                    + ".catch(function(e){window.__masao=JSON.stringify({err:String(e)});});";
            webView.evaluateJavascript(js, null);
            pollResult(0);
        } catch (Exception e) {
            showError("Couldn't build request. Tap to retry.");
        }
    }

    private void pollResult(int waited) {
        h.postDelayed(() -> {
            if (isFinishing() || webView == null) return;
            webView.evaluateJavascript("window.__masao", v -> {
                String raw = unquote(v);
                if (!raw.isEmpty()) { parse(raw); return; }
                if (waited >= RESULT_GIVE_UP_MS) { showError("Timed out. Tap to retry."); return; }
                pollResult(waited + RESULT_POLL_MS);
            });
        }, RESULT_POLL_MS);
    }

    private static JSONObject okObj(JSONArray arr, int i) {
        JSONObject o = arr.optJSONObject(i);
        return (o == null || o.optBoolean("error")) ? null : o;
    }

    private void parse(String raw) {
        try {
            if (raw.startsWith("{")) { showError("Network error. Tap to retry."); return; }
            JSONArray arr = new JSONArray(raw);
            String firstError = null;
            int ok = 0;

            for (int i = 0; i < 3; i++) {
                List<Course> set = courseSets.get(i);
                set.clear();
                JSONObject o = okObj(arr, i);
                if (o == null) {
                    JSONObject bad = arr.optJSONObject(i);
                    if (bad != null && firstError == null) firstError = bad.optJSONObject("exception") != null
                            ? bad.optJSONObject("exception").optString("message") : "error";
                    continue;
                }
                ok++;
                JSONObject data = o.optJSONObject("data");
                JSONArray cs = data != null ? data.optJSONArray("courses") : null;
                if (cs == null) continue;
                for (int j = 0; j < cs.length(); j++) set.add(new Course(cs.getJSONObject(j)));
            }

            timeline.clear();
            JSONObject o3 = okObj(arr, 3);
            if (o3 != null) {
                ok++;
                JSONObject d = o3.optJSONObject("data");
                JSONArray es = d != null ? d.optJSONArray("events") : null;
                if (es != null) for (int j = 0; j < es.length(); j++) timeline.add(new Event(es.getJSONObject(j)));
            }

            recentCourses.clear();
            JSONObject o4 = okObj(arr, 4);
            if (o4 != null) {
                ok++;
                JSONArray a = o4.optJSONArray("data");
                if (a != null) for (int j = 0; j < a.length(); j++) recentCourses.add(new Course(a.getJSONObject(j)));
            }

            recentItems.clear();
            JSONObject o5 = okObj(arr, 5);
            if (o5 != null) {
                ok++;
                JSONArray a = o5.optJSONArray("data");
                if (a != null) for (int j = 0; j < a.length(); j++) recentItems.add(new Event(a.getJSONObject(j)));
            }

            upcoming.clear();
            JSONObject o6 = okObj(arr, 6);
            if (o6 != null) {
                ok++;
                JSONObject d = o6.optJSONObject("data");
                JSONArray es = d != null ? d.optJSONArray("events") : null;
                if (es != null) for (int j = 0; j < es.length(); j++) upcoming.add(new Event(es.getJSONObject(j)));
            }

            int notif = 0, msgs = 0;
            JSONObject o7 = okObj(arr, 7);
            if (o7 != null) notif = o7.optInt("data", 0);
            JSONObject o8 = okObj(arr, 8);
            if (o8 != null) {
                JSONObject d = o8.optJSONObject("data");
                JSONObject t = d != null ? d.optJSONObject("types") : null;
                if (t != null) {
                    Iterator<String> it = t.keys();
                    while (it.hasNext()) msgs += t.optInt(it.next(), 0);
                }
            }

            if (ok == 0) { showError("Session expired or blocked (" + firstError + "). Tap to retry."); return; }

            tvState.setText("");
            tvState.setVisibility(View.GONE);
            tvBell.setText(notif > 0 ? "\uD83D\uDD14 " + notif : "\uD83D\uDD14");
            tvMsg.setText(msgs > 0 ? "\uD83D\uDCAC " + msgs : "\uD83D\uDCAC");
            renderTimeline();
            renderRecentCourses();
            fillRows(llRecentItems, tvRiEmpty, recentItems, false);
            fillRows(llUpcoming, tvUpEmpty, upcoming, true);
            showCourses(0);
            if (tabs.getSelectedTabPosition() == 0) loadFront();
        } catch (Exception e) {
            showError("Couldn't read the response. Tap to retry.");
        }
    }

    // ---------- rendering ----------

    private void setAvatar(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : name.trim().split("\\s+"))
            if (!w.isEmpty() && sb.length() < 2) sb.append(Character.toUpperCase(w.charAt(0)));
        tvAvatar.setText(sb.length() == 0 ? "?" : sb.toString());
    }

    private void addHeader(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextSize(15);
        int p = (int) (8 * getResources().getDisplayMetrics().density);
        t.setPadding(p, p * 2, p, p / 2);
        parent.addView(t);
    }

    private View rowView(ViewGroup parent, Event e, String when, boolean showCourse) {
        View v = getLayoutInflater().inflate(R.layout.item_masao_event, parent, false);
        ((TextView) v.findViewById(R.id.tvEventName)).setText(e.name);
        TextView c = v.findViewById(R.id.tvEventCourse);
        c.setText(e.course);
        c.setVisibility(showCourse && !e.course.isEmpty() ? View.VISIBLE : View.GONE);
        ((TextView) v.findViewById(R.id.tvEventWhen)).setText(when);
        if (!e.url.isEmpty()) v.setOnClickListener(x -> openCourse(e.url, e.name));
        return v;
    }

    private void fillRows(LinearLayout parent, TextView empty, List<Event> items, boolean withTime) {
        parent.removeAllViews();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        for (Event e : items) {
            String when = withTime && e.timeSec > 0 ? fullFmt.format(new Date(e.timeSec * 1000)) : "";
            parent.addView(rowView(parent, e, when, true));
        }
    }

    private void renderTimeline() {
        llTimeline.removeAllViews();
        long now = System.currentTimeMillis() / 1000;
        String q = etTlSearch.getText() == null ? "" : etTlSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Event> list = new ArrayList<>();
        for (Event e : timeline) {
            if (filterIdx == 1 && e.timeSec >= now) continue;
            if (filterIdx >= 2 && (e.timeSec < now || e.timeSec > now + FILTER_DAYS[filterIdx] * 86400L)) continue;
            if (!q.isEmpty() && !(e.name + " " + e.course + " " + e.action).toLowerCase(Locale.ROOT).contains(q)) continue;
            list.add(e);
        }
        if (sortByCourse) {
            Collections.sort(list, (a, b) -> {
                int c = a.course.compareToIgnoreCase(b.course);
                return c != 0 ? c : Long.compare(a.timeSec, b.timeSec);
            });
        }
        tvTlEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        String last = null;
        for (Event e : list) {
            Date d = new Date(e.timeSec * 1000);
            String key = sortByCourse ? (e.course.isEmpty() ? "Other" : e.course) : dayFmt.format(d);
            if (!key.equals(last)) { addHeader(llTimeline, key); last = key; }
            String when = sortByCourse ? fullFmt.format(d) : timeFmt.format(d);
            if (!e.action.isEmpty()) when += " \u00b7 " + e.action;
            llTimeline.addView(rowView(llTimeline, e, when, !sortByCourse));
        }
    }

    private void renderRecentCourses() {
        llRecentCourses.removeAllViews();
        tvRcEmpty.setVisibility(recentCourses.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inf = getLayoutInflater();
        for (Course c : recentCourses) {
            View v = inf.inflate(R.layout.item_masao_recent_course, llRecentCourses, false);
            ((TextView) v.findViewById(R.id.tvRcCat)).setText(c.category);
            ((TextView) v.findViewById(R.id.tvRcName)).setText(c.name);
            ProgressBar pb = v.findViewById(R.id.pbRc);
            TextView pct = v.findViewById(R.id.tvRcPct);
            boolean has = c.progress >= 0;
            pb.setVisibility(has ? View.VISIBLE : View.GONE);
            pct.setVisibility(has ? View.VISIBLE : View.GONE);
            if (has) { pb.setProgress(c.progress); pct.setText(c.progress + "% complete"); }
            v.setOnClickListener(x -> openCourse(c.url, c.name));
            llRecentCourses.addView(v);
        }
    }

    private void showCourses(int set) {
        shownCourses.clear();
        shownCourses.addAll(courseSets.get(set));
        courseAdapter.notifyDataSetChanged();
        tvNoCourses.setVisibility(shownCourses.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showError(String msg) {
        tvState.setVisibility(View.VISIBLE);
        tvState.setText(msg);
    }

    // ---------- navigation ----------

    private void openInWeb(String url) {
        if (url == null || url.isEmpty()) return;
        if (url.contains("myschool.carsu.edu.ph")) {
            try {
                Intent i = new Intent(this, MainActivity.class);
                if (url.contains("/profile")) {
                    i.putExtra("target", "profile");
                }
                startActivity(i);
                return;
            } catch (Exception ignored) { }
        }
        if (url.contains("/pluginfile.php/") || url.endsWith(".pdf") || url.endsWith(".ppt") || url.endsWith(".pptx") || url.endsWith(".docx")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return;
            } catch (Exception ignored) { }
        }
        scrollHome.setVisibility(View.GONE);
        webView.loadUrl(url);
    }

    private void showHome() {
        scrollHome.setVisibility(View.VISIBLE);
        llCourseView.setVisibility(View.GONE);
        llProfileView.setVisibility(View.GONE);
        TabLayout.Tab t = tabs.getTabAt(1);
        if (t != null && !t.isSelected()) t.select();
        if (done && !sesskey.isEmpty()) loadHome();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (llProfileView.getVisibility() == View.VISIBLE) hideProfileView();
            else if (llCourseView.getVisibility() == View.VISIBLE) hideCourseView();
            else if (done && scrollHome.getVisibility() != View.VISIBLE) showHome();
            else finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }

    // ---------- models ----------

    static class Course {
        final String name, shortName, url, category;
        final int progress; // -1 = no progress tracking

        Course(JSONObject j) {
            name = j.optString("fullname", "");
            shortName = j.optString("shortname", "");
            category = j.optString("coursecategory", "");
            String id = j.optString("id", "");
            String view = j.optString("viewurl", "");
            url = !view.isEmpty() ? view : BASE_URL + "/course/view.php?id=" + id;
            progress = j.optBoolean("hasprogress", false) ? (int) Math.round(j.optDouble("progress", 0)) : -1;
        }
    }

    static class Event {
        final String name, course, url, action;
        final long timeSec;

        Event(JSONObject j) {
            name = j.optString("name", "");
            JSONObject c = j.optJSONObject("course");
            course = c != null ? c.optString("fullname", "") : j.optString("coursename", "");
            String u = j.optString("url", "");
            url = !u.isEmpty() ? u : j.optString("viewurl", "");
            JSONObject a = j.optJSONObject("action");
            action = a != null ? a.optString("name", "") : "";
            long t = j.optLong("timesort", 0);
            if (t == 0) t = j.optLong("timestart", 0);
            if (t == 0) t = j.optLong("timeaccess", 0);
            timeSec = t;
        }
    }

    interface UrlClick { void open(String url, String title); }

    // ---------- adapter (My courses tab) ----------

    static class CourseAdapter extends RecyclerView.Adapter<CourseAdapter.VH> {
        private final List<Course> items;
        private final UrlClick click;

        CourseAdapter(List<Course> items, UrlClick click) { this.items = items; this.click = click; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_masao_course, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Course c = items.get(pos);
            h.short_.setText(c.shortName);
            h.name.setText(c.name);
            boolean has = c.progress >= 0;
            h.bar.setVisibility(has ? View.VISIBLE : View.GONE);
            h.pct.setVisibility(has ? View.VISIBLE : View.GONE);
            if (has) {
                h.bar.setProgress(c.progress);
                h.pct.setText(c.progress + "% complete");
            }
            h.itemView.setOnClickListener(v -> click.open(c.url, c.name));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView short_, name, pct;
            final ProgressBar bar;
            VH(@NonNull View v) {
                super(v);
                short_ = v.findViewById(R.id.tvCourseShort);
                name = v.findViewById(R.id.tvCourseName);
                pct = v.findViewById(R.id.tvCourseProgress);
                bar = v.findViewById(R.id.pbCourse);
            }
        }
    }
}