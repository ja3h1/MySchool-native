package com.myschool.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    // Changed to Base URLs so we can append dynamic semesters
    static final String GRADES_URL = "https://csu-api.carsu.edu.ph/api/student/grade/list?show=";
    static final String CLEARANCE_URL_BASE = "https://csu-api.carsu.edu.ph/api/student/clearance/list?status=Not%20Cleared&showAll=true&idNumber=";
    static final String EVAL_URL_BASE = "https://csu-api.carsu.edu.ph/api/student/evaluation/teacher/list?";
    static final String SCHEDULE_URL_BASE = "https://csu-api.carsu.edu.ph/api/student/class-schedule/list?";
    static final String PROFILE_URL = "https://csu-api.carsu.edu.ph/api/user/student/info";
    static final String EMAIL_URL = "https://csu-api.carsu.edu.ph/api/googleworkspace/student/status";
    static final String CURRICULUM_URL = "https://csu-api.carsu.edu.ph/api/student/curriculum/data";
    static final String MENU_URL = "https://csu-api.carsu.edu.ph/api/user/student/menu";
    static final String PROGRAM_DETAILS_URL = "https://csu-api.carsu.edu.ph/api/user/student/program-details";
    static final String CURRENT_TERM_URL = "https://csu-api.carsu.edu.ph/api/general/current-term";
    static final String COURSE_VALIDATION_URL = "https://csu-api.carsu.edu.ph/api/student/course-validation/data";

    static final String[] SCHOOL_YEARS = {"2026-2027", "2025-2026"};
    static final String[] SEMS = {"1st", "2nd"};
    static final String[] DAY_NAMES = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    private static final long SESSION_DURATION_MS = 12 * 60 * 1000L;
    private CountDownTimer sessionTimer;
    private TextView tvSessionTimer;

    private String token, cookies, studentId;
    private View layoutLogin;
    private LinearLayout layoutLoading, layoutDashboard, layoutGradesDetail, layoutClearance, clearanceList, layoutSessionEnded;
    private TextView tvStudentName, tvProgram, tvGradesStudentName, tvGradesProgram, tvGpa, tvLoadingMessage, tvClearanceCount, tvListTitle;
    private RecyclerView recyclerViewGrades;
    private MaterialButton btnLogin, btnRelogin;
    private TextView btnFilter;
    private TextView btnGradesFilter, tvGpaLabel;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private LoginController login;
    private boolean checkingSession;

    // Filter states
    private String schSy = SCHOOL_YEARS[0], schSem = SEMS[0];
    private String grSy = SCHOOL_YEARS[0], grSem = SEMS[0];
    private String clSy = SCHOOL_YEARS[0], clSem = SEMS[0];
    private String evSy = SCHOOL_YEARS[0], evSem = SEMS[0];
    private int currYearFilter = 0; // 0 = All, 1 = 1st Year, 2 = 2nd Year, etc.
    private boolean grAll;

    // Sort states & raw data
    private int gradesSortMode = 0;
    private int clearanceSortMode = 0;
    private int scheduleSortMode = 0;
    private int curriculumSortMode = 0;
    private int evaluationSortMode = 0;

    private View btnGradesSort;
    private View btnSort;
    private TextView tvEmpty;
    private View scrollDays;
    private com.google.android.material.chip.ChipGroup chipGroupDays;
    private int selectedDayFilter = -1; // -1 = Today (default), -2 = All Days, 0..5 = Mon..Sat

    private final List<Grade> rawGradesList = new ArrayList<>();
    private final List<JSONObject> rawClearanceList = new ArrayList<>();
    private final List<Slot> rawScheduleSlots = new ArrayList<>();
    private final Map<String, String> rawScheduleTeachers = new HashMap<>();
    private final List<JSONObject> rawCurriculumData = new ArrayList<>();
    private final List<JSONObject> rawEvaluationData = new ArrayList<>();
    private JSONObject rawEvaluationJson;

    private final OnBackPressedCallback backCb = new OnBackPressedCallback(false) {
        @Override public void handleOnBackPressed() { showState(3); }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        layoutLogin = findViewById(R.id.layoutLogin);
        layoutLoading = findViewById(R.id.layoutLoading);
        layoutDashboard = findViewById(R.id.layoutDashboard);
        layoutGradesDetail = findViewById(R.id.layoutGradesDetail);
        layoutClearance = findViewById(R.id.layoutClearance);
        clearanceList = findViewById(R.id.clearanceList);
        layoutSessionEnded = findViewById(R.id.layoutSessionEnded);

        tvStudentName = findViewById(R.id.tvStudentName);
        tvProgram = findViewById(R.id.tvProgram);
        tvGradesStudentName = findViewById(R.id.tvGradesStudentName);
        tvGradesProgram = findViewById(R.id.tvGradesProgram);
        tvGpa = findViewById(R.id.tvGpa);
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage);
        tvClearanceCount = findViewById(R.id.tvClearanceCount);
        tvListTitle = findViewById(R.id.tvListTitle);
        tvSessionTimer = findViewById(R.id.tvSessionTimer);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);

        View btnMenuDrawer = findViewById(R.id.btnMenuDrawer);
        if (btnMenuDrawer != null) {
            btnMenuDrawer.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        View btnMasaoLms = findViewById(R.id.btnMasaoLms);
        if (btnMasaoLms != null) {
            btnMasaoLms.setOnClickListener(v -> openMasaoLms());
        }

        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                if (id == R.id.nav_schedule) {
                    loadSchedule();
                } else if (id == R.id.nav_grades) {
                    loadGrades();
                } else if (id == R.id.nav_clearance) {
                    loadClearance();
                } else if (id == R.id.nav_evaluation) {
                    loadEvaluation();
                } else if (id == R.id.nav_curriculum) {
                    loadCurriculum();
                } else if (id == R.id.nav_profile) {
                    loadProfile();
                } else if (id == R.id.nav_masaolms) {
                    openMasaoLms();
                } else if (id == R.id.nav_logout) {
                    stopSessionTimer();
                    token = cookies = studentId = null;
                    WebStorage.getInstance().deleteAllData();
                    CookieManager cookieManager = CookieManager.getInstance();
                    cookieManager.removeAllCookies(done -> {
                        cookieManager.flush();
                        runOnUiThread(() -> { showState(1); login.restart(); });
                    });
                }
                return true;
            });
        }

        if ("profile".equals(getIntent().getStringExtra("target"))) {
            getIntent().removeExtra("target");
            loadProfile();
        }

        recyclerViewGrades = findViewById(R.id.recyclerViewGrades);
        recyclerViewGrades.setLayoutManager(new LinearLayoutManager(this));

        btnLogin = findViewById(R.id.btnLogin);
        btnRelogin = findViewById(R.id.btnRelogin);
        btnFilter = findViewById(R.id.btnFilter);
        btnGradesFilter = findViewById(R.id.btnGradesFilter);
        tvGpaLabel = findViewById(R.id.tvGpaLabel);

        // Unified Filter Button Listener
        if (btnFilter != null) {
            btnFilter.setOnClickListener(v -> {
                String title = tvListTitle.getText().toString();
                if (title.contains("Schedule")) pickScheduleSemester();
                else if (title.contains("Clearance")) pickClearanceSemester();
                else if (title.contains("Evaluation")) pickEvaluationSemester();
                else if (title.contains("Curriculum")) pickCurriculumYear();
            });
        }

        if (btnGradesFilter != null) btnGradesFilter.setOnClickListener(v -> pickGradesFilter());

        btnGradesSort = findViewById(R.id.btnGradesSort);
        if (btnGradesSort != null) {
            btnGradesSort.setOnClickListener(v -> {
                gradesSortMode = (gradesSortMode + 1) % 3;
                renderGrades();
            });
        }

        btnSort = findViewById(R.id.btnSort);
        if (btnSort != null) {
            btnSort.setOnClickListener(v -> {
                String title = tvListTitle.getText().toString();
                if (title.contains("Schedule")) {
                    scheduleSortMode = (scheduleSortMode + 1) % 3;
                    renderSchedule();
                } else if (title.contains("Clearance")) {
                    clearanceSortMode = (clearanceSortMode + 1) % 3;
                    renderClearance();
                } else if (title.contains("Evaluation")) {
                    evaluationSortMode = (evaluationSortMode + 1) % 3;
                    renderEvaluation();
                } else if (title.contains("Curriculum")) {
                    curriculumSortMode = (curriculumSortMode + 1) % 3;
                    renderCurriculum();
                }
            });
        }

        tvEmpty = findViewById(R.id.tvEmpty);
        scrollDays = findViewById(R.id.scrollDays);
        chipGroupDays = findViewById(R.id.chipGroupDays);

        login = new LoginController(this, btnLogin);
        if (btnRelogin != null) btnRelogin.setOnClickListener(v -> { showState(1); login.restart(); });

        View btnClearRelogin = findViewById(R.id.btnClearRelogin);
        if (btnClearRelogin != null) {
            btnClearRelogin.setOnClickListener(v -> {
                token = cookies = studentId = null;
                WebStorage.getInstance().deleteAllData();
                CookieManager.getInstance().removeAllCookies(done -> runOnUiThread(() -> { showState(1); login.restart(); }));
            });
        }

        setupMenuItem(R.id.menuClearance, "My Clearance", "Pending requirements", R.drawable.ic_clearance);
        setupMenuItem(R.id.menuSchedule, "Class Schedule", "Weekly class schedule", R.drawable.ic_schedule);
        setupMenuItem(R.id.menuGrades, "Grades", "Academic grades & GPA", R.drawable.ic_grades);
        setupMenuItem(R.id.menuCurriculum, "My Curriculum", "Subject requirements", R.drawable.ic_curriculum);
        setupMenuItem(R.id.menuEvaluation, "My Evaluation", "Teacher evaluation", R.drawable.ic_evaluation);
        setupMenuItem(R.id.menuProfile, "My Profile", "Student information", R.drawable.ic_profile);

        View cardNextClass = findViewById(R.id.cardNextClass);
        if (cardNextClass != null) cardNextClass.setOnClickListener(v -> loadSchedule());

        View menuGrades = findViewById(R.id.menuGrades);
        if (menuGrades != null) menuGrades.setOnClickListener(v -> showState(3.5));

        View menuSchedule = findViewById(R.id.menuSchedule);
        if (menuSchedule != null) menuSchedule.setOnClickListener(v -> loadSchedule());
        View menuCurriculum = findViewById(R.id.menuCurriculum);
        if (menuCurriculum != null) menuCurriculum.setOnClickListener(v -> loadCurriculum());
        View menuClearance = findViewById(R.id.menuClearance);
        if (menuClearance != null) menuClearance.setOnClickListener(v -> loadClearance());
        View menuEvaluation = findViewById(R.id.menuEvaluation);
        if (menuEvaluation != null) menuEvaluation.setOnClickListener(v -> loadEvaluation());

        View btnBackFromClearance = findViewById(R.id.btnBackFromClearance);
        if (btnBackFromClearance != null) btnBackFromClearance.setOnClickListener(v -> showState(3));
        View btnBackHome = findViewById(R.id.btnBackHome);
        if (btnBackHome != null) btnBackHome.setOnClickListener(v -> showState(3));

        View.OnClickListener logoutListener = v -> {
            stopSessionTimer();
            token = cookies = studentId = null;
            WebStorage.getInstance().deleteAllData();
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(done -> {
                cookieManager.flush();
                runOnUiThread(() -> { showState(1); login.restart(); });
            });
        };

        View btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) btnLogout.setOnClickListener(logoutListener);

        View menuProfile = findViewById(R.id.menuProfile);
        if (menuProfile != null) menuProfile.setOnClickListener(v -> loadProfile());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else if (backCb.isEnabled()) {
                    showState(3);
                } else {
                    finish();
                }
            }
        });
        for (int id : new int[]{R.id.btnLogin, R.id.btnRelogin, R.id.btnLogout,
                R.id.btnBackHome, R.id.btnBackFromClearance, R.id.menuSchedule,
                R.id.menuClearance, R.id.menuEvaluation, R.id.menuCurriculum, R.id.menuGrades,
                R.id.menuProfile, R.id.btnFilter, R.id.btnGradesFilter, R.id.btnSort, R.id.btnGradesSort}) {
            View v = findViewById(id);
            if (v != null) pressable(v);
        }

        String savedCookie = CookieManager.getInstance().getCookie("https://myschool.carsu.edu.ph");
        if (savedCookie != null && !savedCookie.trim().isEmpty()) {
            checkingSession = true;
            tvLoadingMessage.setText("Checking saved session...");
            showState(2);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (checkingSession) { checkingSession = false; showState(1); }
            }, 8000);
        } else {
            showState(1);
        }
    }

    // --- FILTER DIALOGS ---

    private void pickScheduleSemester() {
        showSemesterDialog(schSy, schSem, (sy, sem) -> {
            schSy = sy; schSem = sem;
            loadSchedule();
        });
    }

    private void pickClearanceSemester() {
        showSemesterDialog(clSy, clSem, (sy, sem) -> {
            clSy = sy; clSem = sem;
            loadClearance();
        });
    }

    private void pickEvaluationSemester() {
        showSemesterDialog(evSy, evSem, (sy, sem) -> {
            evSy = sy; evSem = sem;
            loadEvaluation();
        });
    }

    private void showSemesterDialog(String currentSy, String currentSem, OnSemesterPicked listener) {
        String[] labels = new String[SCHOOL_YEARS.length * SEMS.length];
        for (int i = 0; i < labels.length; i++)
            labels[i] = SCHOOL_YEARS[i / SEMS.length].replace('-', '–') + ", " + SEMS[i % SEMS.length] + " semester";
        int checked = Arrays.asList(SCHOOL_YEARS).indexOf(currentSy) * SEMS.length + Arrays.asList(SEMS).indexOf(currentSem);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Semester")
                .setSingleChoiceItems(labels, checked, (d, i) -> {
                    d.dismiss();
                    listener.onPicked(SCHOOL_YEARS[i / SEMS.length], SEMS[i % SEMS.length]);
                })
                .show();
    }

    private interface OnSemesterPicked {
        void onPicked(String sy, String sem);
    }

    private void pickCurriculumYear() {
        String[] labels = {"All Years", "1st Year", "2nd Year", "3rd Year", "4th Year"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Filter by Year Level")
                .setSingleChoiceItems(labels, currYearFilter, (d, i) -> {
                    d.dismiss();
                    currYearFilter = i;
                    loadCurriculum(); // Reloads and filters
                })
                .show();
    }

    private void pickGradesFilter() {
        int n = SCHOOL_YEARS.length * SEMS.length;
        String[] labels = new String[n + 1];
        labels[0] = "All semesters";
        for (int i = 0; i < n; i++)
            labels[i + 1] = SCHOOL_YEARS[i / SEMS.length].replace('-', '–') + ", " + SEMS[i % SEMS.length] + " semester";
        int checked = grAll ? 0 : 1 + Arrays.asList(SCHOOL_YEARS).indexOf(grSy) * SEMS.length + Arrays.asList(SEMS).indexOf(grSem);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Show grades for")
                .setSingleChoiceItems(labels, checked, (d, i) -> {
                    d.dismiss();
                    grAll = i == 0;
                    if (!grAll) {
                        grSy = SCHOOL_YEARS[(i - 1) / SEMS.length];
                        grSem = SEMS[(i - 1) % SEMS.length];
                    }
                    loadGrades();
                })
                .show();
    }

    // --- LOADERS ---

    private void loadClearance() {
        tvLoadingMessage.setText("Fetching clearance...");
        showState(2);
        String url = CLEARANCE_URL_BASE + studentId + "&sy=" + clSy + "&sem=" + clSem;
        new Thread(() -> {
            try {
                JSONObject json = fetchJson(url);
                runOnUiThread(() -> {
                    if (json == null) { showState(4); return; }
                    tvListTitle.setText("My Clearance");
                    if (btnFilter != null) {
                        btnFilter.setText(clSy.replace('-', '–') + ", " + clSem + " sem  ▾");
                        btnFilter.setVisibility(View.VISIBLE);
                    }
                    showClearance(json.optJSONArray("data"));
                    showState(3.6);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showState(4));
            }
        }).start();
    }

    private void loadEvaluation() {
        tvLoadingMessage.setText("Fetching evaluation...");
        showState(2);
        String url = EVAL_URL_BASE + "sy=" + evSy + "&sem=" + evSem;
        new Thread(() -> {
            try {
                JSONObject json = fetchJson(url);
                runOnUiThread(() -> {
                    if (json == null) { showState(4); return; }
                    tvListTitle.setText("My Evaluation");
                    if (btnFilter != null) {
                        btnFilter.setText(evSy.replace('-', '–') + ", " + evSem + " sem  ▾");
                        btnFilter.setVisibility(View.VISIBLE);
                    }
                    showEvaluation(json);
                    showState(3.6);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showState(4));
            }
        }).start();
    }

    private void loadSchedule() {
        tvLoadingMessage.setText("Fetching schedule...");
        showState(2);
        String url = SCHEDULE_URL_BASE + "sy=" + schSy + "&sem=" + schSem;
        new Thread(() -> {
            try {
                JSONObject json = fetchJson(url);
                runOnUiThread(() -> {
                    if (json == null) { showState(4); return; }
                    showSchedule(json);
                    showState(3.6);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showState(4));
            }
        }).start();
    }

    private void loadCurriculum() {
        tvLoadingMessage.setText("Fetching curriculum...");
        showState(2);
        new Thread(() -> {
            try {
                JSONObject json = fetchJson(CURRICULUM_URL);
                runOnUiThread(() -> {
                    if (json == null) { showState(4); return; }
                    tvListTitle.setText("My Curriculum");
                    if (btnFilter != null) {
                        String label = currYearFilter == 0 ? "All Years" : currYearFilter + " Year";
                        btnFilter.setText(label + "  ▾");
                        btnFilter.setVisibility(View.VISIBLE);
                    }
                    showCurriculum(json);
                    showState(3.6);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showState(4));
            }
        }).start();
    }

    // --- VIEW BUILDERS & SORTING ---

    private static double parseGradeValue(String g) {
        if (g == null || g.isEmpty() || g.equals("—")) return 99.0;
        if (g.equalsIgnoreCase("PASSED")) return 1.0;
        try {
            return Double.parseDouble(g);
        } catch (Exception e) {
            return 98.0;
        }
    }

    private void renderGrades() {
        if (rawGradesList == null) return;
        List<Grade> list = new ArrayList<>(rawGradesList);
        if (gradesSortMode == 0) {
            Collections.sort(list, (a, b) -> a.getCourseCode().compareToIgnoreCase(b.getCourseCode()));
            if (btnGradesSort != null) {
                ((TextView) btnGradesSort).setText("Sort: Subject");
            }
        } else if (gradesSortMode == 1) {
            Collections.sort(list, (a, b) -> Double.compare(parseGradeValue(a.getGrade()), parseGradeValue(b.getGrade())));
            if (btnGradesSort != null) {
                ((TextView) btnGradesSort).setText("Sort: Grade");
            }
        } else {
            Collections.sort(list, (a, b) -> b.getSortKey().compareTo(a.getSortKey()));
            if (btnGradesSort != null) {
                ((TextView) btnGradesSort).setText("Sort: Term");
            }
        }
        recyclerViewGrades.setAdapter(new GradesAdapter(list, grAll));
    }

    private void renderClearance() {
        if (rawClearanceList == null) return;
        List<JSONObject> list = new ArrayList<>(rawClearanceList);
        if (clearanceSortMode == 0) {
            Collections.sort(list, (a, b) -> b.optString("dateposted").compareTo(a.optString("dateposted")));
            if (btnSort != null) ((TextView) btnSort).setText("Sort: Newest");
        } else if (clearanceSortMode == 1) {
            Collections.sort(list, (a, b) -> a.optString("dateposted").compareTo(b.optString("dateposted")));
            if (btnSort != null) ((TextView) btnSort).setText("Sort: Oldest");
        } else {
            Collections.sort(list, (a, b) -> a.optString("office").compareToIgnoreCase(b.optString("office")));
            if (btnSort != null) ((TextView) btnSort).setText("Sort: Office");
        }
        clearanceList.removeAllViews();
        if (tvEmpty != null) tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        for (JSONObject o : list) {
            View v = getLayoutInflater().inflate(R.layout.item_clearance, clearanceList, false);
            ((TextView) v.findViewById(R.id.tvReason)).setText(o.optString("reason"));
            ((TextView) v.findViewById(R.id.tvOffice)).setText(o.optString("office"));
            ((TextView) v.findViewById(R.id.tvRemarks)).setText(o.optString("remarks").trim());
            String d = o.optString("dateposted");
            ((TextView) v.findViewById(R.id.tvPosted)).setText(
                    "Posted " + (d.length() >= 10 ? d.substring(0, 10) : d) + " by " + o.optString("postedby"));
            clearanceList.addView(v);
        }
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density);
    }

    private void setupScheduleDaysFilter() {
        if (chipGroupDays == null) return;
        chipGroupDays.removeAllViews();
        String[] labels = {"Today", "All", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setText(labels[i]);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipMinHeight((float) dp(34));
            if ((selectedDayFilter == -1 && index == 0) || (selectedDayFilter == -2 && index == 1) || (selectedDayFilter >= 0 && index == selectedDayFilter + 2)) {
                chip.setChecked(true);
            }
            chip.setOnClickListener(v -> {
                if (index == 0) selectedDayFilter = -1;
                else if (index == 1) selectedDayFilter = -2;
                else selectedDayFilter = index - 2;
                renderSchedule();
            });
            chipGroupDays.addView(chip);
        }
    }

    private void updateNextClassCard() {
        int todayIndex = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7;
        Slot nextSlot = null;

        for (Slot s : rawScheduleSlots) {
            if (s.day == (todayIndex < 6 ? todayIndex : 0)) {
                nextSlot = s;
                break;
            }
        }

        if (nextSlot == null && !rawScheduleSlots.isEmpty()) {
            nextSlot = rawScheduleSlots.get(0);
        }

        View cardNextClass = findViewById(R.id.cardNextClass);
        if (cardNextClass != null) {
            TextView tvLabel = cardNextClass.findViewById(R.id.tvNextClassLabel);
            TextView tvTitle = cardNextClass.findViewById(R.id.tvNextClassTitle);
            TextView tvMeta = cardNextClass.findViewById(R.id.tvNextClassMeta);

            if (nextSlot != null) {
                JSONObject o = nextSlot.o;
                String room = o.optString("room");
                String dayName = nextSlot.day == todayIndex ? "Today (" + DAY_NAMES[nextSlot.day] + ")" : DAY_NAMES[nextSlot.day];

                if (tvLabel != null) tvLabel.setText("Class Schedule · " + dayName);
                if (tvTitle != null) tvTitle.setText(o.optString("description"));
                if (tvMeta != null) tvMeta.setText(fmtTime(o.optString("fromtime")) + " to " + fmtTime(o.optString("totime")) + " · " + (room.isEmpty() || room.equals("TBA") ? "Room TBA" : room));
            } else {
                if (tvLabel != null) tvLabel.setText("Class Schedule");
                if (tvTitle != null) tvTitle.setText("No classes scheduled");
                if (tvMeta != null) tvMeta.setText("Tap to view your schedule");
            }
        }
    }

    private void renderSchedule() {
        if (rawScheduleSlots == null) return;
        List<Slot> slots = new ArrayList<>(rawScheduleSlots);
        int todayIndex = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7;

        int activeDay = -1;
        if (selectedDayFilter == -1) {
            activeDay = todayIndex < 6 ? todayIndex : 0;
        } else if (selectedDayFilter >= 0) {
            activeDay = selectedDayFilter;
        }

        if (activeDay >= 0) {
            final int filterDay = activeDay;
            List<Slot> daySlots = new ArrayList<>();
            for (Slot s : slots) {
                if (s.day == filterDay) daySlots.add(s);
            }
            slots = daySlots;
        }

        if (scheduleSortMode == 0) {
            Collections.sort(slots, (a, b) -> a.day != b.day ? a.day - b.day : a.o.optString("fromtime").compareTo(b.o.optString("fromtime")));
            if (btnSort != null) ((TextView) btnSort).setText("Sort: Day & Time");
        } else if (scheduleSortMode == 1) {
            Collections.sort(slots, (a, b) -> a.o.optString("fromtime").compareTo(b.o.optString("fromtime")));
            if (btnSort != null) ((TextView) btnSort).setText("Sort: Time");
        } else {
            Collections.sort(slots, (a, b) -> a.o.optString("description").compareToIgnoreCase(b.o.optString("description")));
            if (btnSort != null) ((TextView) btnSort).setText("Sort: Subject");
        }

        clearanceList.removeAllViews();
        if (tvEmpty != null) {
            if (slots.isEmpty()) {
                String dayName = activeDay >= 0 ? DAY_NAMES[Math.min(activeDay, 5)] : "selected day";
                tvEmpty.setText("No classes scheduled for " + dayName + ".");
                tvEmpty.setVisibility(View.VISIBLE);
            } else {
                tvEmpty.setVisibility(View.GONE);
            }
        }

        int shown = -1;
        for (Slot sl : slots) {
            if ((selectedDayFilter == -2 || activeDay < 0) && scheduleSortMode == 0 && sl.day != shown) {
                shown = sl.day;
                TextView h = new TextView(this);
                h.setText(DAY_NAMES[shown] + (shown == todayIndex ? ", today" : ""));
                h.setTextSize(14);
                h.setTypeface(null, Typeface.BOLD);
                h.setTextColor(getColor(shown == todayIndex ? R.color.primary : R.color.on_surface_variant));
                h.setPadding(0, (int) (clearanceList.getChildCount() == 0 ? 0 : 14 * getResources().getDisplayMetrics().density),
                        0, (int) (8 * getResources().getDisplayMetrics().density));
                clearanceList.addView(h);
            }
            JSONObject o = sl.o;
            String room = o.optString("room"), bldg = o.optString("bldg");
            boolean tba = room.equals("TBA") || room.isEmpty();
            View v = getLayoutInflater().inflate(R.layout.item_schedule, clearanceList, false);
            ((TextView) v.findViewById(R.id.tvStart)).setText(fmtTime(o.optString("fromtime")));
            ((TextView) v.findViewById(R.id.tvEnd)).setText("to " + fmtTime(o.optString("totime")));
            ((TextView) v.findViewById(R.id.tvSubject)).setText(o.optString("description"));
            ((TextView) v.findViewById(R.id.tvCourse)).setText(o.optString("courseno") + ", section " + o.optString("section"));
            ((TextView) v.findViewById(R.id.tvRoom)).setText(tba ? "Room to be announced"
                    : bldg.equals("ONLINE") ? "Online, " + room : room + ", " + titleCase(bldg));
            String t = rawScheduleTeachers.get(o.optString("courseno") + o.optString("section"));
            TextView tvT = v.findViewById(R.id.tvTeacher);
            if (t == null) tvT.setVisibility(View.GONE); else tvT.setText(t);
            clearanceList.addView(v);
        }
    }

    private void renderCurriculum() {
        if (rawCurriculumData == null) return;
        List<JSONObject> data = new ArrayList<>(rawCurriculumData);
        if (curriculumSortMode == 0) {
            Collections.sort(data, (a, b) -> {
                int y1 = a.optInt("yearlevel"), y2 = b.optInt("yearlevel");
                if (y1 != y2) return y1 - y2;
                return a.optString("sem").compareTo(b.optString("sem"));
            });
            if (btnSort != null) ((TextView) btnSort).setText("Sort: Year");
        } else if (curriculumSortMode == 1) {
            Collections.sort(data, (a, b) -> {
                boolean p1 = a.optString("grade").equals("PASSED");
                boolean p2 = b.optString("grade").equals("PASSED");
                if (p1 != p2) return p1 ? -1 : 1;
                return a.optString("description").compareToIgnoreCase(b.optString("description"));
            });
            if (btnSort != null) ((TextView) btnSort).setText("Sort: Passed");
        } else {
            Collections.sort(data, (a, b) -> a.optString("description").compareToIgnoreCase(b.optString("description")));
            if (btnSort != null) ((TextView) btnSort).setText("Sort: Subject");
        }
        clearanceList.removeAllViews();
        if (tvEmpty != null) tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);

        int total = 0, passed = 0, now = 0;
        String last = "";
        for (JSONObject o : data) {
            int yl = o.optInt("yearlevel");
            if (yl >= 15) continue;
            if (currYearFilter > 0 && yl / 10 != currYearFilter) continue;

            String sem = o.optString("sem");
            if (curriculumSortMode == 0 && !(yl + sem).equals(last)) {
                last = yl + sem;
                addHeader(yl == 10 ? "Electives" : "Year " + yl + ", " + sem + " semester");
            }
            boolean pass = o.optString("grade").equals("PASSED"), cur = o.optBoolean("currently_enrolled");
            total++;
            if (pass) passed++;
            if (cur) now++;
            View v = getLayoutInflater().inflate(R.layout.item_clearance, clearanceList, false);
            TextView title = v.findViewById(R.id.tvReason);
            title.setText(o.optString("description").trim());
            title.setTextColor(getColor(R.color.on_surface));
            ((TextView) v.findViewById(R.id.tvOffice)).setText(o.optString("courseno") + ", " + o.optString("unit") + " units");
            String pre = o.optString("prerequisite").trim();
            TextView tvPre = v.findViewById(R.id.tvRemarks);
            if (pre.isEmpty()) tvPre.setVisibility(View.GONE); else tvPre.setText("Prerequisite: " + pre);
            TextView st = v.findViewById(R.id.tvPosted);
            st.setText(pass ? "Passed" : cur ? "Enrolled this semester" : "Not taken yet");
            if (pass) st.setTextColor(getColor(R.color.primary));
            if (pass || cur) st.setTypeface(null, Typeface.BOLD);
            clearanceList.addView(v);
        }
        tvClearanceCount.setText(passed + " of " + total + " subjects passed, " + now + " enrolled now");

        View menuCurriculum = findViewById(R.id.menuCurriculum);
        if (menuCurriculum != null) {
            TextView st = menuCurriculum.findViewById(R.id.tileStatus);
            if (st != null) st.setText(passed + " of " + total + " passed");
        }
    }

    private void renderEvaluation() {
        if (rawEvaluationData == null) return;
        List<JSONObject> list = new ArrayList<>(rawEvaluationData);
        if (evaluationSortMode == 0) {
            Collections.sort(list, (a, b) -> Boolean.compare(a.optBoolean("evaluated"), b.optBoolean("evaluated")));
            if (btnSort != null) ((TextView) btnSort).setText("Sort: To Do");
        } else if (evaluationSortMode == 1) {
            Collections.sort(list, (a, b) -> Boolean.compare(b.optBoolean("evaluated"), a.optBoolean("evaluated")));
            if (btnSort != null) ((TextView) btnSort).setText("Sort: Evaluated");
        } else {
            Collections.sort(list, (a, b) -> a.optString("facultyname").compareToIgnoreCase(b.optString("facultyname")));
            if (btnSort != null) ((TextView) btnSort).setText("Sort: Faculty");
        }
        clearanceList.removeAllViews();
        if (tvEmpty != null) tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

        int n = list.size(), done = 0;
        for (JSONObject o : list) {
            boolean ev = o.optBoolean("evaluated");
            if (ev) done++;
            View v = getLayoutInflater().inflate(R.layout.item_clearance, clearanceList, false);
            TextView title = v.findViewById(R.id.tvReason);
            title.setText(o.optString("description"));
            title.setTextColor(getColor(R.color.on_surface));
            ((TextView) v.findViewById(R.id.tvOffice)).setText(o.optString("facultyname"));
            ((TextView) v.findViewById(R.id.tvRemarks)).setText(o.optString("courseno") + ", section " + o.optString("section"));
            ((TextView) v.findViewById(R.id.tvPosted)).setText(ev ? "Evaluated" : "Not evaluated yet");
            clearanceList.addView(v);
        }
        JSONObject sched = rawEvaluationJson != null ? rawEvaluationJson.optJSONObject("evaluationSchedule") : null;
        String end = sched == null ? "" : sched.optString("enddate");
        boolean closed = rawEvaluationJson != null && rawEvaluationJson.optBoolean("evaluationClosed");
        tvClearanceCount.setText(done + " of " + n + " evaluated. "
                + (closed ? "Closed " : "Open until ") + end);

        View menuEvaluation = findViewById(R.id.menuEvaluation);
        if (menuEvaluation != null) {
            TextView st = menuEvaluation.findViewById(R.id.tileStatus);
            if (st != null) st.setText(done + " of " + n + " evaluated");
        }
    }

    private void showCurriculum(JSONObject json) {
        JSONArray data = json.optJSONArray("curriculum");
        rawCurriculumData.clear();
        for (int i = 0; data != null && i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o != null) rawCurriculumData.add(o);
        }
        if (btnSort != null) btnSort.setVisibility(View.VISIBLE);
        if (scrollDays != null) scrollDays.setVisibility(View.GONE);
        renderCurriculum();
    }

    private void showSchedule(JSONObject json) {
        JSONArray sched = json.optJSONArray("schedule"), courses = json.optJSONArray("courses");
        rawScheduleTeachers.clear();
        for (int i = 0; courses != null && i < courses.length(); i++) {
            JSONObject c = courses.optJSONObject(i);
            if (c != null) rawScheduleTeachers.put(c.optString("courseno") + c.optString("section"),
                    titleCase(c.optString("firstname") + " " + c.optString("lastname")));
        }

        rawScheduleSlots.clear();
        for (int i = 0; sched != null && i < sched.length(); i++) {
            JSONObject o = sched.optJSONObject(i);
            if (o == null) continue;
            for (int d : parseDays(o.optString("days"))) {
                Slot sl = new Slot();
                sl.day = d;
                sl.o = o;
                rawScheduleSlots.add(sl);
            }
        }

        tvListTitle.setText("My Class Schedule");
        tvClearanceCount.setText(rawScheduleSlots.isEmpty() ? "No classes scheduled"
                : rawScheduleSlots.size() + " class meetings a week");
        if (btnFilter != null) {
            btnFilter.setText(schSy.replace('-', '–') + ", " + schSem + " semester  ▾");
            btnFilter.setVisibility(View.VISIBLE);
        }
        if (btnSort != null) btnSort.setVisibility(View.VISIBLE);
        if (scrollDays != null) scrollDays.setVisibility(View.VISIBLE);

        setupScheduleDaysFilter();
        updateNextClassCard();
        renderSchedule();
    }

    // ... (Keep setupMenuItem, pressable, showState, fetchJson, showProfile, addField, addHeader, showClearance, showEvaluation, gradesUrl, loadGrades, showGrades, startSessionTimer, stopSessionTimer, formatRemaining, onDestroy, onLoggedIn exactly as they are, except for the changes in clear/eval) ...

    private void setupMenuItem(int includeId, String title, String status, int iconRes) {
        View item = findViewById(includeId);
        if (item != null) {
            TextView tv = item.findViewById(R.id.tvMenuTitle);
            if (tv == null) tv = item.findViewById(R.id.tileTitle);
            ImageView img = item.findViewById(R.id.imgMenuIcon);
            if (img == null) img = item.findViewById(R.id.tileIcon);
            TextView st = item.findViewById(R.id.tileStatus);

            if (tv != null) tv.setText(title);
            if (img != null) img.setImageResource(iconRes);
            if (st != null && status != null) st.setText(status);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN)
                view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).setInterpolator(null).start();
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)
                view.animate().scaleX(1f).scaleY(1f).setDuration(240)
                        .setInterpolator(new OvershootInterpolator(2.5f)).start();
            return false;
        });
    }

    private void showState(double state) {
        TransitionManager.beginDelayedTransition(
                (ViewGroup) findViewById(android.R.id.content), new Fade().setDuration(180));
        backCb.setEnabled(state == 3.5 || state == 3.6);
        if (state == 2 && btnFilter != null) btnFilter.setVisibility(View.GONE);
        layoutLogin.setVisibility(state == 1 ? View.VISIBLE : View.GONE);
        layoutLoading.setVisibility(state == 2 ? View.VISIBLE : View.GONE);
        layoutDashboard.setVisibility(state == 3 ? View.VISIBLE : View.GONE);
        layoutGradesDetail.setVisibility(state == 3.5 ? View.VISIBLE : View.GONE);
        layoutClearance.setVisibility(state == 3.6 ? View.VISIBLE : View.GONE);
        layoutSessionEnded.setVisibility(state == 4 ? View.VISIBLE : View.GONE);
    }

    private JSONObject fetchJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        if (cookies != null && !cookies.isEmpty()) conn.setRequestProperty("Cookie", cookies);
        conn.setRequestProperty("Authorization", token);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36");
        if (conn.getResponseCode() != 200) return null;
        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return new JSONObject(sb.toString());
    }

    private void loadProfile() {
        tvLoadingMessage.setText("Fetching profile...");
        showState(2);
        new Thread(() -> {
            try {
                JSONObject info = fetchJson(PROFILE_URL);
                JSONObject prog = fetchJson(PROGRAM_DETAILS_URL);
                JSONObject mail = fetchJson(EMAIL_URL);
                JSONObject term = fetchJson(CURRENT_TERM_URL);
                runOnUiThread(() -> {
                    if (info == null && prog == null) { showState(4); return; }
                    showProfile(
                            info != null ? info.optJSONObject("data") : null,
                            prog != null ? prog.optJSONObject("data") : null,
                            mail,
                            term != null ? term.optJSONObject("data") : null
                    );
                    showState(3.6);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showState(4));
            }
        }).start();
    }

    private void showProfile(JSONObject info, JSONObject prog, JSONObject mail, JSONObject term) {
        clearanceList.removeAllViews();
        tvListTitle.setText("My Profile");
        if (btnFilter != null) btnFilter.setVisibility(View.GONE);
        if (btnSort != null) btnSort.setVisibility(View.GONE);
        if (scrollDays != null) scrollDays.setVisibility(View.GONE);
        tvClearanceCount.setText(tvStudentName.getText());

        addHeader("Student & Academic Details");
        addField("Name", tvStudentName.getText().toString());
        addField("Program & Student ID", tvProgram.getText().toString());
        if (prog != null) {
            addField("Degree Program", prog.optString("progdesc"));
            int level = prog.optInt("studlevel", 0);
            if (level > 0) addField("Year Level", "Year " + level);
            String college = prog.optString("college");
            String campus = prog.optString("campus");
            if (!college.isEmpty()) addField("College & Campus", college + (campus.isEmpty() ? "" : " \u00b7 " + campus + " Campus"));
            addField("Curriculum Year", prog.optString("cur_year"));
        }

        addHeader("Contact & Account Recovery");
        addField("School Email", mail == null ? "" : mail.optString("email"));
        if (info != null) {
            addField("Mobile Number", info.optString("mobile"));
            addField("Account Recovery Code", info.optString("account_recoverycode"));
        }

        if (term != null) {
            addHeader("Current Term Information");
            String sy = term.optString("sy"), sem = term.optString("sem");
            if (!sy.isEmpty()) addField("Academic Term", sy + ", " + sem + " Semester");
            addField("Start of Classes", term.optString("startclass"));
            addField("Add/Change Deadline", term.optString("addchange_deadline"));
            String gradDead = term.optString("gradapp_deadline");
            if (!gradDead.isEmpty()) {
                if (gradDead.contains("T")) gradDead = gradDead.split("T")[0];
                addField("Graduation Application Deadline", gradDead);
            }
        }
    }

    private void addField(String label, String value) {
        if (value.isEmpty()) return;
        View v = getLayoutInflater().inflate(R.layout.item_clearance, clearanceList, false);
        TextView t = v.findViewById(R.id.tvReason);
        t.setText(value);
        t.setTextColor(getColor(R.color.on_surface));
        ((TextView) v.findViewById(R.id.tvOffice)).setText(label);
        v.findViewById(R.id.tvRemarks).setVisibility(View.GONE);
        v.findViewById(R.id.tvPosted).setVisibility(View.GONE);
        clearanceList.addView(v);
    }

    private void addHeader(String text) {
        float d = getResources().getDisplayMetrics().density;
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextSize(14);
        h.setTypeface(null, Typeface.BOLD);
        h.setTextColor(getColor(R.color.on_surface_variant));
        h.setPadding(0, (int) ((clearanceList.getChildCount() == 0 ? 0 : 14) * d), 0, (int) (8 * d));
        clearanceList.addView(h);
    }

    private void showClearance(JSONArray data) {
        int n = data == null ? 0 : data.length();
        tvClearanceCount.setText(n == 0 ? "No pending clearances" : n + " pending");

        rawClearanceList.clear();
        for (int i = 0; i < n; i++) {
            JSONObject o = data.optJSONObject(i);
            if (o != null) rawClearanceList.add(o);
        }

        if (btnSort != null) btnSort.setVisibility(View.VISIBLE);
        if (scrollDays != null) scrollDays.setVisibility(View.GONE);

        View menuClearance = findViewById(R.id.menuClearance);
        if (menuClearance != null) {
            TextView st = menuClearance.findViewById(R.id.tileStatus);
            if (st != null) st.setText(n == 0 ? "No pending clearances" : n + " pending");
        }

        renderClearance();
    }

    private void showEvaluation(JSONObject json) {
        rawEvaluationJson = json;
        JSONArray data = json != null ? json.optJSONArray("data") : null;
        rawEvaluationData.clear();
        for (int i = 0; data != null && i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o != null) rawEvaluationData.add(o);
        }
        if (btnSort != null) btnSort.setVisibility(View.VISIBLE);
        if (scrollDays != null) scrollDays.setVisibility(View.GONE);
        renderEvaluation();
    }

    private String gradesUrl() {
        return GRADES_URL + (grAll ? "All" : "bysem") + "&sy=" + grSy + "&sem=" + grSem;
    }

    private void loadGrades() {
        tvLoadingMessage.setText("Fetching grades...");
        showState(2);
        new Thread(() -> {
            try {
                JSONObject json = fetchJson(gradesUrl());
                runOnUiThread(() -> {
                    if (json == null) { showState(4); return; }
                    showGrades(json);
                    showState(3.5);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showState(4));
            }
        }).start();
    }

    private void showGrades(JSONObject json) {
        JSONArray arr = json.optJSONArray("grades");
        rawGradesList.clear();
        for (int i = 0; arr != null && i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) rawGradesList.add(new Grade(o));
        }
        JSONObject ss = json.optJSONObject("scholStatus");
        double gpa = ss != null ? ss.optDouble("gpa", 0) : 0;
        tvGpa.setText(gpa > 0 ? String.format(Locale.US, "%.2f", gpa) : "—");
        if (tvGpaLabel != null) tvGpaLabel.setText(grAll ? "Pick one semester to see GPA" : "GPA this semester");
        if (btnGradesFilter != null) btnGradesFilter.setText((grAll ? "All semesters" : grSy.replace('-', '–') + ", " + grSem + " semester") + "  ▾");

        View menuGrades = findViewById(R.id.menuGrades);
        if (menuGrades != null) {
            TextView st = menuGrades.findViewById(R.id.tileStatus);
            if (st != null) st.setText(gpa > 0 ? String.format(Locale.US, "GPA %.2f", gpa) : "Academic grades & GPA");
        }

        renderGrades();
    }

    private void startSessionTimer() {
        stopSessionTimer();
        String initial = formatRemaining(SESSION_DURATION_MS);
        if (tvSessionTimer != null) tvSessionTimer.setText(initial);

        sessionTimer = new CountDownTimer(SESSION_DURATION_MS, 1000) {
            @Override public void onTick(long msLeft) {
                String t = formatRemaining(msLeft);
                if (tvSessionTimer != null) tvSessionTimer.setText(t);
            }
            @Override public void onFinish() {
                if (tvSessionTimer != null) tvSessionTimer.setText("Session expired");
                token = cookies = studentId = null;
                showState(4);
            }
        }.start();
    }

    private void stopSessionTimer() {
        if (sessionTimer != null) {
            sessionTimer.cancel();
            sessionTimer = null;
        }
    }

    private static String formatRemaining(long msLeft) {
        long totalSec = msLeft / 1000;
        return String.format(Locale.US, "Session ends in %d:%02d", totalSec / 60, totalSec % 60);
    }

    @Override
    protected void onDestroy() {
        stopSessionTimer();
        super.onDestroy();
    }

    private void loadCourseValidation() {
        tvLoadingMessage.setText("Fetching course validation...");
        showState(2);
        new Thread(() -> {
            try {
                JSONObject json = fetchJson(COURSE_VALIDATION_URL);
                runOnUiThread(() -> {
                    if (json == null) { showState(4); return; }
                    tvListTitle.setText("Course Validation");
                    if (btnFilter != null) btnFilter.setVisibility(View.GONE);
                    if (btnSort != null) btnSort.setVisibility(View.GONE);
                    if (scrollDays != null) scrollDays.setVisibility(View.GONE);
                    showCourseValidation(json);
                    showState(3.6);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showState(4));
            }
        }).start();
    }

    private void showCourseValidation(JSONObject json) {
        clearanceList.removeAllViews();
        boolean transferee = json.optBoolean("isTransferee", false);
        boolean enrolled = json.optBoolean("isCurrentlyEnrolled", false);
        boolean applied = json.optBoolean("isAlreadyApplied", false);

        tvClearanceCount.setText(enrolled ? "Currently Enrolled" : "Not Currently Enrolled");

        addField("Transferee Status", transferee ? "Yes (Transferee)" : "No (Regular Student)");
        addField("Currently Enrolled", enrolled ? "Yes" : "No");
        addField("Application Status", applied ? "Already Applied" : "Not Yet Applied");

        JSONArray courseDetails = json.optJSONArray("courseDetails");
        if (courseDetails != null && courseDetails.length() > 0) {
            addHeader("Course Details");
            for (int i = 0; i < courseDetails.length(); i++) {
                JSONObject o = courseDetails.optJSONObject(i);
                if (o != null) {
                    addField(o.optString("title", "Course"), o.optString("description", ""));
                }
            }
        }

        JSONArray data = json.optJSONArray("data");
        if (data != null && data.length() > 0) {
            addHeader("Validation Data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject o = data.optJSONObject(i);
                if (o != null) {
                    addField("Item " + (i + 1), o.toString());
                }
            }
        }
    }

    private void fetchProgramDetails() {
        new Thread(() -> {
            try {
                JSONObject json = fetchJson(PROGRAM_DETAILS_URL);
                if (json == null) return;
                JSONObject data = json.optJSONObject("data");
                if (data == null) return;

                String progDesc = data.optString("progdesc");
                String college = data.optString("college");
                int level = data.optInt("studlevel");
                String campus = data.optString("campus");

                String detail = progDesc + (!college.isEmpty() ? " (" + college + ")" : "") + (level > 0 ? ", Year " + level : "") + (!campus.isEmpty() ? " - " + campus : "");
                runOnUiThread(() -> {
                    if (tvProgram != null) tvProgram.setText(detail);
                    TextView tvGradesProgram = findViewById(R.id.tvGradesProgram);
                    if (tvGradesProgram != null) tvGradesProgram.setText(detail);

                    if (navigationView != null) {
                        View header = navigationView.getHeaderView(0);
                        if (header != null) {
                            TextView navProg = header.findViewById(R.id.tvNavProgram);
                            if (navProg != null) navProg.setText(detail);
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void fetchAndSetupSidebarMenu() {
        new Thread(() -> {
            try {
                JSONObject json = fetchJson(MENU_URL);
                if (json == null) return;
                JSONArray sidebar = json.optJSONArray("sidebar");
                if (sidebar == null) return;

                runOnUiThread(() -> {
                    if (navigationView == null) return;
                    android.view.Menu menu = navigationView.getMenu();
                    menu.clear();

                    for (int i = 0; i < sidebar.length(); i++) {
                        JSONObject item = sidebar.optJSONObject(i);
                        if (item == null) continue;
                        String title = item.optString("title");
                        int id = 1000 + item.optInt("id", i);

                        int iconRes = R.drawable.ic_profile;
                        String tLower = title.toLowerCase(Locale.US);
                        if (tLower.contains("schedule")) iconRes = R.drawable.ic_schedule;
                        else if (tLower.contains("grade")) iconRes = R.drawable.ic_grades;
                        else if (tLower.contains("clearance")) iconRes = R.drawable.ic_clearance;
                        else if (tLower.contains("curriculum")) iconRes = R.drawable.ic_curriculum;
                        else if (tLower.contains("evaluation")) iconRes = R.drawable.ic_evaluation;

                        menu.add(0, id, i, title).setIcon(iconRes);
                    }

                    menu.add(0, 9999, 999, "Log out").setIcon(android.R.drawable.ic_menu_close_clear_cancel);

                    navigationView.setNavigationItemSelectedListener(menuItem -> {
                        if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                        int id = menuItem.getItemId();
                        String title = menuItem.getTitle() != null ? menuItem.getTitle().toString() : "";

                        if (id == 9999 || title.equalsIgnoreCase("Log out")) {
                            stopSessionTimer();
                            token = cookies = studentId = null;
                            WebStorage.getInstance().deleteAllData();
                            CookieManager cookieManager = CookieManager.getInstance();
                            cookieManager.removeAllCookies(done -> {
                                cookieManager.flush();
                                runOnUiThread(() -> { showState(1); login.restart(); });
                            });
                            return true;
                        }

                        String tLower = title.toLowerCase(Locale.US);
                        if (tLower.contains("schedule")) {
                            loadSchedule();
                        } else if (tLower.contains("grade")) {
                            loadGrades();
                        } else if (tLower.contains("clearance")) {
                            loadClearance();
                        } else if (tLower.contains("curriculum")) {
                            loadCurriculum();
                        } else if (tLower.contains("evaluation")) {
                            loadEvaluation();
                        } else if (tLower.contains("validation") || tLower.contains("course validation")) {
                            loadCourseValidation();
                        } else if (tLower.contains("masaolms") || tLower.contains("lms")) {
                            openMasaoLms();
                            return true;
                        } else if (tLower.contains("profile") || tLower.contains("info")) {
                            loadProfile();
                        } else {
                            Toast.makeText(this, title + " coming soon", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    });
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void openMasaoLms() {
        try {
            startActivity(new android.content.Intent(this, MasaoLmsActivity.class));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open masaoLMS", Toast.LENGTH_SHORT).show();
        }
    }

    void onLoginReady() {
        if (checkingSession) { checkingSession = false; showState(1); }
    }

    void onLoggedIn(String token, String cookies) {
        this.token = token;
        this.cookies = cookies;

        runOnUiThread(() -> {
            showState(2);
            tvLoadingMessage.setText("Fetching academic records...");
        });
        new Thread(() -> {
            try {
                JSONObject json = fetchJson(gradesUrl());
                if (json == null) { runOnUiThread(() -> showState(4)); return; }
                JSONObject info = json.optJSONObject("info");
                String name = "", prog = "", id = "";
                if (info != null) {
                    name = titleCase(info.optString("firstname") + " " + info.optString("lastname"));
                    prog = info.optString("progdesc", info.optString("shorthand", info.optString("studmajor", "")));
                    id = info.optString("studid");
                    studentId = id;
                }
                String finalName = name;
                String detail = prog.isEmpty() ? id : id.isEmpty() ? prog : prog + " - [" + id + "]";
                fetchProgramDetails();
                fetchAndSetupSidebarMenu();
                runOnUiThread(() -> {
                    tvStudentName.setText(finalName);
                    tvProgram.setText(detail);
                    tvGradesStudentName.setText(finalName);
                    tvGradesProgram.setText(detail);

                    if (navigationView != null) {
                        View header = navigationView.getHeaderView(0);
                        if (header != null) {
                            TextView navName = header.findViewById(R.id.tvNavStudentName);
                            TextView navProg = header.findViewById(R.id.tvNavProgram);
                            if (navName != null) navName.setText(finalName);
                            if (navProg != null) navProg.setText(detail);
                        }
                    }

                    startSessionTimer();
                    showGrades(json);
                    showState(3);
                    if ("profile".equals(getIntent().getStringExtra("target"))) {
                        getIntent().removeExtra("target");
                        loadProfile();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showState(4));
            }
        }).start();
    }

    // Helper methods that were in the original file
    private static class Slot { int day; JSONObject o; }
    private static List<Integer> parseDays(String s) {
        List<Integer> r = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 'T' && i + 1 < s.length() && s.charAt(i + 1) == 'h') { r.add(3); i++; }
            else { int d = "MTW.FS".indexOf(c); if (d >= 0) r.add(d); }
        }
        return r;
    }
    private static String fmtTime(String t) {
        if (t == null || t.length() < 5) return t;
        int h = Integer.parseInt(t.substring(0, 2));
        return (h % 12 == 0 ? 12 : h % 12) + t.substring(2, 5) + (h < 12 ? " AM" : " PM");
    }
    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.trim().toLowerCase(Locale.US).split("\\s+"))
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }
}
