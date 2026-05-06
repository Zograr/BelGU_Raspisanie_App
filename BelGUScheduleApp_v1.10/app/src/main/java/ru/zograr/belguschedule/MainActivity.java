package ru.zograr.belguschedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String PREFS = "schedule_cache";
    private static final String KEY_TEXT = "last_text";
    private static final String KEY_DATE = "last_date";
    private static final String KEY_UPDATED = "last_updated";
    private static final String KEY_GROUP = "selected_group";
    private static final String KEY_SUBGROUP = "selected_subgroup";
    private static final String KEY_SELECTED_DAY = "selected_day";
    private static final String KEY_SHOW_WEEK = "show_whole_week";
    private static final String DEFAULT_GROUP = "90002595";
    private static final String PLACEHOLDER_OWNER = "YOUR_GITHUB_USERNAME";
    private static final String SCHEDULE_BASE_URL = "https://bsuedu.ru/bsu/education/schedule/groups/index.php?group=";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String SUBGROUP_ALL = "all";
    private static final String SUBGROUP_1 = "1";
    private static final String SUBGROUP_2 = "2";
    private static final int MAX_EXTRACT_ATTEMPTS = 4;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Locale ru = new Locale("ru", "RU");

    private LinearLayout rootLayout;
    private LinearLayout headerLayout;
    private LinearLayout controlsLayout;
    private LinearLayout listLayout;
    private TextView statusView;
    private TextView groupPill;
    private TextView titleView;
    private TextView dateView;
    private TextView summaryView;
    private ProgressBar progressBar;
    private Button refreshButton;
    private Button openSiteButton;
    private Button subgroupMenuButton;
    private Button dayMenuButton;
    private Button updateButton;
    private TextView themeToggleButton;
    private TextView settingsButton;
    private WebView hiddenWebView;

    private String scheduleUrl;
    private String scheduleGroup = DEFAULT_GROUP;
    private String selectedSubgroup = SUBGROUP_ALL;
    private int selectedDayIndex = 0;
    private boolean showWholeWeek = false;
    private boolean darkMode = false;
    private ScheduleResult currentResult;
    private boolean mainFrameError = false;
    private long updateDownloadId = -1L;
    private BroadcastReceiver updateDownloadReceiver;

    private int greenDark;
    private int green;
    private int greenSoft;
    private int background;
    private int cardSurface;
    private int textMain;
    private int textMuted;
    private int border;
    private int headerStart;
    private int headerEnd;
    private int roomChipBg;
    private int roomChipStroke;
    private int buildingChipBg;
    private int buildingChipStroke;
    private int buildingChipText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        scheduleGroup = prefs.getString(KEY_GROUP, DEFAULT_GROUP);
        scheduleUrl = buildScheduleUrl(scheduleGroup);
        selectedSubgroup = prefs.getString(KEY_SUBGROUP, SUBGROUP_ALL);
        selectedDayIndex = prefs.getInt(KEY_SELECTED_DAY, currentDayIndex());
        if (selectedDayIndex < 0 || selectedDayIndex > 6) selectedDayIndex = currentDayIndex();
        showWholeWeek = prefs.getBoolean(KEY_SHOW_WEEK, false);
        darkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        applyPalette();
        buildUi();
        setupWebView();
        registerUpdateDownloadReceiver();
        loadCachedSchedule();
        refreshSchedule();
    }

    @Override
    protected void onDestroy() {
        if (hiddenWebView != null) {
            hiddenWebView.stopLoading();
            hiddenWebView.destroy();
        }
        if (updateDownloadReceiver != null) {
            try {
                unregisterReceiver(updateDownloadReceiver);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    private void buildUi() {
        applyPalette();
        getWindow().setStatusBarColor(headerStart);
        getWindow().setNavigationBarColor(background);

        LinearLayout root = new LinearLayout(this);
        rootLayout = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        headerLayout = header;
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(14), getStatusBarHeight() + dp(8), dp(14), dp(16));
        header.setBackground(headerBackground());
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout headerTop = new LinearLayout(this);
        headerTop.setOrientation(LinearLayout.HORIZONTAL);
        headerTop.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(headerTop, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        groupPill = new TextView(this);
        groupPill.setText("Группа " + scheduleGroup + "  ✎");
        groupPill.setTextColor(Color.rgb(225, 247, 228));
        groupPill.setTextSize(13);
        groupPill.setTypeface(Typeface.DEFAULT_BOLD);
        groupPill.setPadding(dp(12), dp(5), dp(12), dp(5));
        groupPill.setBackground(pillBackground(Color.argb(48, 255, 255, 255), Color.argb(70, 255, 255, 255)));
        groupPill.setOnClickListener(v -> showGroupDialog());
        headerTop.addView(groupPill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        settingsButton = new TextView(this);
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setText("⚙");
        settingsButton.setTextSize(22);
        settingsButton.setTypeface(Typeface.DEFAULT_BOLD);
        settingsButton.setTextColor(Color.WHITE);
        settingsButton.setBackground(pillBackground(Color.argb(52, 255, 255, 255), Color.argb(90, 255, 255, 255)));
        settingsButton.setOnClickListener(v -> showSettingsDialog());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        settingsParams.setMargins(dp(10), 0, 0, 0);
        headerTop.addView(settingsButton, settingsParams);

        titleView = new TextView(this);
        titleView.setText("Расписание");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(28);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setPadding(0, dp(12), 0, 0);
        header.addView(titleView);

        dateView = new TextView(this);
        dateView.setText("Сегодня");
        dateView.setTextColor(Color.rgb(222, 246, 224));
        dateView.setTextSize(15);
        dateView.setPadding(0, dp(3), 0, 0);
        header.addView(dateView);

        summaryView = new TextView(this);
        summaryView.setText("Загружаю пары...");
        summaryView.setTextColor(Color.WHITE);
        summaryView.setTextSize(15);
        summaryView.setTypeface(Typeface.DEFAULT_BOLD);
        summaryView.setPadding(0, dp(10), 0, 0);
        header.addView(summaryView);

        updateSubgroupUi();

        LinearLayout controls = new LinearLayout(this);
        controlsLayout = controls;
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(12), dp(12), dp(12), dp(8));
        root.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        refreshButton = new Button(this);
        refreshButton.setText("Обновить");
        refreshButton.setAllCaps(false);
        refreshButton.setTextColor(Color.WHITE);
        refreshButton.setTextSize(15);
        refreshButton.setTypeface(Typeface.DEFAULT_BOLD);
        refreshButton.setBackground(buttonBackground(green, green));
        controls.addView(refreshButton, new LinearLayout.LayoutParams(0, dp(46), 1));

        openSiteButton = new Button(this);
        openSiteButton.setText("Открыть сайт");
        openSiteButton.setAllCaps(false);
        openSiteButton.setTextColor(greenDark);
        openSiteButton.setTextSize(15);
        openSiteButton.setTypeface(Typeface.DEFAULT_BOLD);
        openSiteButton.setBackground(buttonBackground(cardSurface, border));
        LinearLayout.LayoutParams siteParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        siteParams.setMargins(dp(10), 0, 0, 0);
        controls.addView(openSiteButton, siteParams);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        progressParams.setMargins(dp(10), 0, 0, 0);
        controls.addView(progressBar, progressParams);

        statusView = new TextView(this);
        statusView.setTextColor(textMuted);
        statusView.setTextSize(13);
        statusView.setPadding(dp(18), 0, dp(18), dp(8));
        root.addView(statusView);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setClipToPadding(false);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(dp(12), dp(4), dp(12), dp(20));
        scrollView.addView(listLayout);

        hiddenWebView = new WebView(this);
        hiddenWebView.setAlpha(0f);
        root.addView(hiddenWebView, new LinearLayout.LayoutParams(1, 1));

        refreshButton.setOnClickListener(v -> refreshSchedule());
        openSiteButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(scheduleUrl)));
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String buildScheduleUrl(String group) {
        return SCHEDULE_BASE_URL + group;
    }

    private String cacheKey(String key) {
        return key + "_" + scheduleGroup + "_week";
    }

    private void showGroupDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(scheduleGroup);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(Color.rgb(20, 28, 22));
        input.setHintTextColor(Color.rgb(120, 130, 123));
        input.setHint("Номер группы");
        input.setPadding(dp(8), dp(8), dp(8), dp(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Выбор группы")
                .setMessage("Введи номер своей группы")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newGroup = input.getText().toString().trim();
            if (!newGroup.matches("\\d{4,12}")) {
                Toast.makeText(this, "Введи только цифры номера группы", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newGroup.equals(scheduleGroup)) {
                dialog.dismiss();
                return;
            }
            scheduleGroup = newGroup;
            scheduleUrl = buildScheduleUrl(scheduleGroup);
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_GROUP, scheduleGroup)
                    .apply();
            updateSubgroupUi();
            listLayout.removeAllViews();
            statusView.setText("Загружаю расписание группы " + scheduleGroup + "...");
            summaryView.setText("Обновляю данные...");
            currentResult = null;
            dialog.dismiss();
            loadCachedSchedule();
            refreshSchedule();
        }));
        dialog.show();
    }

    private void showSettingsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(4));

        TextView info = new TextView(this);
        info.setText("Группа " + scheduleGroup + " • " + subgroupDisplay() + "\n" +
                (showWholeWeek ? "Сейчас показывается вся неделя" : "Сейчас показывается только сегодня"));
        info.setTextColor(Color.rgb(60, 70, 63));
        info.setTextSize(14);
        info.setPadding(0, 0, 0, dp(12));
        box.addView(info);

        Button themeButton = settingsDialogButton("Тема: " + (darkMode ? "тёмная ☾" : "светлая ☀"));
        themeButton.setOnClickListener(v -> {
            toggleTheme();
            Toast.makeText(this, "Тема изменена", Toast.LENGTH_SHORT).show();
        });
        box.addView(themeButton);

        Button subgroupButton = settingsDialogButton("Подгруппа: " + subgroupDisplayShort());
        subgroupButton.setOnClickListener(v -> showSubgroupDialog());
        box.addView(subgroupButton);

        Button groupButton = settingsDialogButton("Изменить группу");
        groupButton.setOnClickListener(v -> showGroupDialog());
        box.addView(groupButton);

        Button weekButton = settingsDialogButton(showWholeWeek ? "Показывать только сегодня" : "Показать все пары на неделе");
        weekButton.setOnClickListener(v -> {
            showWholeWeek = !showWholeWeek;
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_SHOW_WEEK, showWholeWeek)
                    .apply();
            updateSubgroupUi();
            if (currentResult != null) showSchedule(currentResult, true);
            Toast.makeText(this, showWholeWeek ? "Показываю всю неделю" : "Показываю только сегодня", Toast.LENGTH_SHORT).show();
        });
        box.addView(weekButton);

        Button updateCheckButton = settingsDialogButton("Проверить обновления приложения");
        updateCheckButton.setOnClickListener(v -> checkForUpdates(true));
        box.addView(updateCheckButton);

        new AlertDialog.Builder(this)
                .setTitle("Настройки")
                .setView(box)
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private Button settingsDialogButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(greenDark);
        button.setBackground(buttonBackground(greenSoft, border));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        params.setMargins(0, 0, 0, dp(10));
        button.setLayoutParams(params);
        return button;
    }

    private void showSubgroupDialog() {
        String[] items = {"1 подгруппа", "2 подгруппа", "Все подгруппы"};
        int checked = SUBGROUP_1.equals(selectedSubgroup) ? 0 : SUBGROUP_2.equals(selectedSubgroup) ? 1 : 2;
        new AlertDialog.Builder(this)
                .setTitle("Выбор подгруппы")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    if (which == 0) selectSubgroup(SUBGROUP_1);
                    if (which == 1) selectSubgroup(SUBGROUP_2);
                    if (which == 2) selectSubgroup(SUBGROUP_ALL);
                    dialog.dismiss();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void selectDay(int dayIndex) {
        selectedDayIndex = currentDayIndex();
    }

    private void selectSubgroup(String subgroup) {
        selectedSubgroup = subgroup;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_SUBGROUP, selectedSubgroup)
                .apply();
        updateSubgroupUi();
        if (currentResult != null) {
            showSchedule(currentResult, true);
            statusView.setText("Показана " + subgroupDisplay().toLowerCase(ru) + ".");
        }
    }

    private void updateSubgroupUi() {
        if (groupPill != null) {
            groupPill.setText("Группа " + scheduleGroup + " • " + subgroupDisplayShort());
        }
        if (settingsButton != null) {
            settingsButton.setText("⚙");
        }
        if (themeToggleButton != null) {
            themeToggleButton.setText(darkMode ? "☾" : "☀");
        }
        if (subgroupMenuButton != null) {
            subgroupMenuButton.setText(subgroupDisplayShort() + "  ▾");
        }
        if (dayMenuButton != null) {
            dayMenuButton.setText(showWholeWeek ? "Вся неделя" : "Сегодня");
        }
    }

    private String subgroupDisplayShort() {
        if (SUBGROUP_1.equals(selectedSubgroup)) return "1 подгруппа";
        if (SUBGROUP_2.equals(selectedSubgroup)) return "2 подгруппа";
        return "Все";
    }

    private String subgroupDisplay() {
        if (SUBGROUP_1.equals(selectedSubgroup)) return "1 подгруппа";
        if (SUBGROUP_2.equals(selectedSubgroup)) return "2 подгруппа";
        return "все подгруппы";
    }

    private void applyPalette() {
        if (darkMode) {
            // Material You dark: спокойный зелёный + мягкие tonal containers.
            greenDark = Color.rgb(190, 239, 202);
            green = Color.rgb(91, 214, 126);
            greenSoft = Color.rgb(31, 48, 38);
            background = Color.rgb(17, 22, 18);
            cardSurface = Color.rgb(28, 34, 30);
            textMain = Color.rgb(236, 242, 235);
            textMuted = Color.rgb(185, 197, 187);
            border = Color.rgb(59, 70, 61);
            headerStart = Color.rgb(18, 60, 33);
            headerEnd = Color.rgb(36, 96, 52);
            roomChipBg = Color.rgb(37, 48, 40);
            roomChipStroke = Color.rgb(75, 105, 82);
            buildingChipBg = Color.rgb(31, 58, 40);
            buildingChipStroke = Color.rgb(78, 129, 88);
            buildingChipText = Color.rgb(220, 255, 226);
        } else {
            // Material You light: пастельные зелёные контейнеры без кислотности.
            greenDark = Color.rgb(28, 94, 47);
            green = Color.rgb(61, 137, 79);
            greenSoft = Color.rgb(225, 241, 229);
            background = Color.rgb(248, 250, 247);
            cardSurface = Color.rgb(255, 255, 252);
            textMain = Color.rgb(28, 32, 29);
            textMuted = Color.rgb(88, 99, 91);
            border = Color.rgb(220, 229, 221);
            headerStart = Color.rgb(24, 86, 42);
            headerEnd = Color.rgb(58, 137, 75);
            roomChipBg = Color.rgb(238, 246, 240);
            roomChipStroke = Color.rgb(193, 215, 198);
            buildingChipBg = Color.rgb(226, 240, 229);
            buildingChipStroke = Color.rgb(155, 191, 164);
            buildingChipText = Color.rgb(29, 93, 47);
        }
    }

    private void toggleTheme() {
        darkMode = !darkMode;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DARK_MODE, darkMode)
                .apply();

        if (rootLayout == null) {
            applyThemeToViews();
            return;
        }

        rootLayout.animate()
                .alpha(0.78f)
                .setDuration(120)
                .withEndAction(() -> {
                    applyPalette();
                    applyThemeToViews();
                    if (currentResult != null) {
                        showSchedule(currentResult, true);
                    }
                    rootLayout.animate().alpha(1f).setDuration(180).start();
                })
                .start();
    }

    private void applyThemeToViews() {
        applyPalette();
        getWindow().setStatusBarColor(headerStart);
        getWindow().setNavigationBarColor(background);
        if (rootLayout != null) rootLayout.setBackgroundColor(background);
        if (headerLayout != null) headerLayout.setBackground(headerBackground());
        if (groupPill != null) groupPill.setBackground(pillBackground(Color.argb(48, 255, 255, 255), Color.argb(70, 255, 255, 255)));
        if (settingsButton != null) {
            settingsButton.setText("⚙");
            settingsButton.setBackground(pillBackground(Color.argb(52, 255, 255, 255), Color.argb(90, 255, 255, 255)));
        }
        if (themeToggleButton != null) {
            themeToggleButton.setText(darkMode ? "☾" : "☀");
            themeToggleButton.setBackground(pillBackground(Color.argb(52, 255, 255, 255), Color.argb(90, 255, 255, 255)));
        }
        if (subgroupMenuButton != null) subgroupMenuButton.setBackground(pillBackground(Color.argb(52, 255, 255, 255), Color.argb(90, 255, 255, 255)));
        if (refreshButton != null) refreshButton.setBackground(buttonBackground(green, green));
        if (openSiteButton != null) {
            openSiteButton.setTextColor(greenDark);
            openSiteButton.setBackground(buttonBackground(cardSurface, border));
        }
        if (statusView != null) statusView.setTextColor(textMuted);
        if (listLayout != null) listLayout.setBackgroundColor(background);
        updateSubgroupUi();
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private void setupWebView() {
        WebSettings settings = hiddenWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(false);
        settings.setBlockNetworkImage(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

        hiddenWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                attemptExtract(1);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    mainFrameError = true;
                }
            }
        });
    }

    private void refreshSchedule() {
        mainFrameError = false;
        setLoading(true);
        statusView.setText("Проверяю изменения в расписании...");
        summaryView.setText("Обновляю данные...");
        try {
            hiddenWebView.stopLoading();
            hiddenWebView.loadUrl(scheduleUrl);
        } catch (Exception e) {
            showLoadFailure("Ошибка запуска загрузки: " + e.getMessage());
        }
    }

    private void attemptExtract(final int attempt) {
        int delay = attempt == 1 ? 1200 : 1800;
        main.postDelayed(() -> {
            if (hiddenWebView == null) return;
            hiddenWebView.evaluateJavascript(buildExtractScript(), value -> handleJsResult(value, attempt));
        }, delay);
    }

    private void handleJsResult(String value, int attempt) {
        try {
            String jsonString = new JSONArray("[" + value + "]").getString(0);
            JSONObject object = new JSONObject(jsonString);
            String date = object.optString("date", todayString());
            String loadedText = object.optString("text", "").trim();
            boolean ok = object.optBoolean("ok", false);

            if (ok && looksLikeSchedule(loadedText)) {
                String newClean = cleanupSection(loadedText);
                SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                String cached = prefs.getString(cacheKey(KEY_TEXT), "");
                String oldClean = cached.isEmpty() ? "" : cleanupSection(cached);

                if (!oldClean.isEmpty() && oldClean.equals(newClean)) {
                    setLoading(false);
                    if (currentResult == null) {
                        ScheduleResult result = parseSchedule(date, cached);
                        showSchedule(result, true);
                    }
                    statusView.setText("Изменений нет • проверено " + nowString());
                    return;
                }

                ScheduleResult result = parseSchedule(date, loadedText);
                saveCache(result.displayDate, result.fullText);
                showSchedule(result, false);
                statusView.setText(cached.isEmpty() ? "Расписание загружено: " + nowString() : "Расписание изменилось • обновлено " + nowString());
                return;
            }

            if (attempt < MAX_EXTRACT_ATTEMPTS && !mainFrameError) {
                statusView.setText("Жду, пока сайт догрузит расписание... попытка " + (attempt + 1));
                attemptExtract(attempt + 1);
                return;
            }

            String debug = object.optString("debug", "");
            if (!debug.isEmpty()) {
                showLoadFailure("Не нашёл блок расписания. Сайт мог изменить разметку.");
            } else {
                showLoadFailure("Не нашёл пары. Возможно, расписание пустое.");
            }
        } catch (Exception e) {
            if (attempt < MAX_EXTRACT_ATTEMPTS && !mainFrameError) {
                attemptExtract(attempt + 1);
            } else {
                showLoadFailure("Ошибка обработки расписания: " + e.getMessage());
            }
        }
    }

    private String buildExtractScript() {
        return "(function(){" +
                "function two(n){return n<10?'0'+n:''+n;}" +
                "var d=new Date();" +
                "var today=two(d.getDate())+'.'+two(d.getMonth()+1)+'.'+d.getFullYear();" +
                "var body=(document.body&&document.body.innerText)?document.body.innerText:'';" +
                "body=body.replace(/\\u00a0/g,' ').replace(/\\r/g,'\\n').replace(/[ \\t]+/g,' ').replace(/\\n[ \\t]+/g,'\\n').replace(/\\n{3,}/g,'\\n\\n');" +
                "var days='Понедельник|Вторник|Среда|Четверг|Пятница|Суббота|Воскресенье';" +
                "var re=new RegExp('(?:^|\\\\n)\\\\s*\\\\d{2}\\\\.\\\\d{2}\\\\.\\\\d{4}\\\\s+(?:'+days+')(?:\\\\s*\\\\(сегодня\\\\))?','ig');" +
                "var m=re.exec(body);" +
                "if(!m){return JSON.stringify({ok:false,date:today,text:'',debug:body.slice(0,1200)});}" +
                "var start=m.index;" +
                "var section=body.slice(start);" +
                "section=section.replace(/\\\\n\\\\s*Ссылка на расписание этой группы[\\\\s\\\\S]*$/i,'');" +
                "section=section.replace(/Нашли ошибку[\\\\s\\\\S]*$/i,'').trim();" +
                "return JSON.stringify({ok:true,date:today,text:section,debug:body.slice(0,800)});" +
                "})()";
    }

    private boolean looksLikeSchedule(String text) {
        String lower = text.toLowerCase(ru);
        return lower.contains("пара") || lower.contains("учебный корпус") || lower.contains("преп.") || lower.contains("ауд.");
    }

    private ScheduleResult parseSchedule(String displayDate, String section) {
        String clean = cleanupSection(section);
        List<DaySchedule> days = splitDays(clean);

        if (days.isEmpty()) {
            List<String> pairs = splitPairs(clean);
            if (pairs.isEmpty()) {
                pairs.add("Пары не найдены. Возможно, занятий нет или сайт временно отдал пустое расписание.");
            }
            days.add(new DaySchedule(displayDate, "Сегодня", clean, pairs));
        }

        DaySchedule today = findTodaySchedule(days);
        List<String> todayPairs = today == null ? new ArrayList<>() : today.pairs;
        return new ScheduleResult(today == null ? displayDate : today.displayDate, clean, todayPairs, days);
    }

    private List<DaySchedule> splitDays(String fullWeekText) {
        List<DaySchedule> result = new ArrayList<>();
        Pattern dayPattern = Pattern.compile("(?im)^\\s*(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(Понедельник|Вторник|Среда|Четверг|Пятница|Суббота|Воскресенье)(?:\\s*\\(сегодня\\))?.*$");
        Matcher matcher = dayPattern.matcher(fullWeekText);

        List<Integer> starts = new ArrayList<>();
        List<String> dates = new ArrayList<>();
        List<String> names = new ArrayList<>();

        while (matcher.find()) {
            starts.add(matcher.start());
            dates.add(matcher.group(1));
            names.add(matcher.group(2));
        }

        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : fullWeekText.length();
            String block = fullWeekText.substring(start, end).trim();
            List<String> pairs = splitPairs(block);
            result.add(new DaySchedule(dates.get(i), names.get(i), block, pairs));
        }

        return result;
    }

    private DaySchedule findTodaySchedule(List<DaySchedule> days) {
        String today = todayString();
        for (DaySchedule day : days) {
            if (today.equals(day.displayDate)) return day;
        }
        return days.isEmpty() ? null : days.get(0);
    }

    private String cleanupSection(String section) {
        String text = section.replace('\u00A0', ' ')
                .replace('–', '-')
                .replace('—', '-')
                .replace("\r", "\n");
        text = text.replaceAll("(?i)Нашли ошибку[\\s\\S]*$", "");
        text = text.replaceAll("(?m)^\\s*Ссылка на расписание этой группы\\s*$", "");
        text = text.replaceAll("(?m)^\\s*Группа\\s+\\d+\\s*$", "");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("(?m)^\\s+|\\s+$", "");
        text = text.replaceAll("\\n{2,}", "\n");
        return text.trim();
    }

    private List<String> splitPairs(String section) {
        List<String> pairs = new ArrayList<>();
        String normalized = section.replace('\u00A0', ' ')
                .replace('–', '-')
                .replace('—', '-')
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{2,}", "\n")
                .trim();

        Pattern pairPattern = Pattern.compile(
                "(?is)(\\d\\s*пара)\\s+([0-2]?\\d:[0-5]\\d\\s*-\\s*[0-2]?\\d:[0-5]\\d)([\\s\\S]*?)(?=\\n?\\s*\\d\\s*пара\\s+[0-2]?\\d:[0-5]\\d|\\n?\\s*\\d{2}\\.\\d{2}\\.\\d{4}|$)");
        Matcher matcher = pairPattern.matcher(normalized);
        while (matcher.find()) {
            String pairNumber = matcher.group(1).replaceAll("\\s+", " ").trim();
            String time = matcher.group(2).replaceAll("\\s*-\\s*", " - ").trim();
            String body = matcher.group(3).trim();

            body = body.replaceAll("(?i)\\bпреп\\.", "\nПреп.:");
            body = body.replaceAll("(?i)\\bауд\\.", "\nАуд.:");
            body = body.replaceAll("(?i)Учебный корпус", "учебный корпус");
            body = body.replaceAll("[ \\t]+", " ");
            body = body.replaceAll("\\n\\s+", "\n");
            body = body.replaceAll("\\n{2,}", "\n").trim();

            String formatted = pairNumber + "  •  " + time;
            if (!body.isEmpty()) formatted += "\n" + body;
            pairs.add(formatted.trim());
        }
        return pairs;
    }

    private void showSchedule(ScheduleResult result, boolean fromCache) {
        currentResult = result;
        setLoading(false);
        listLayout.removeAllViews();

        if (showWholeWeek) {
            showWholeWeekSchedule(result);
        } else {
            showTodaySchedule(result);
        }

        if (!fromCache) {
            statusView.setText("Обновлено: " + nowString());
        }
    }

    private void showTodaySchedule(ScheduleResult result) {
        DaySchedule today = findTodaySchedule(result.days);
        titleView.setText("Расписание");
        dateView.setText(today == null ? "Сегодня" : "Сегодня • " + today.displayDate);
        List<String> visiblePairs = today == null ? new ArrayList<>() : filterPairsForSubgroup(today.pairs);
        summaryView.setText(summaryText(visiblePairs.size(), "Сегодня") + " • " + subgroupDisplay());

        if (visiblePairs.isEmpty()) {
            addMessageCard("Пар не найдено", "Для выбранной подгруппы на сегодня пары не найдены. Можно в настройках включить «всю неделю» или переключиться на «Все».");
        } else {
            for (String pair : visiblePairs) {
                addPairCard(pair);
            }
        }
    }

    private void showWholeWeekSchedule(ScheduleResult result) {
        titleView.setText("Неделя");
        dateView.setText("Все пары на неделе");
        int total = 0;
        for (DaySchedule day : result.days) {
            total += filterPairsForSubgroup(day.pairs).size();
        }
        summaryView.setText(weekSummaryText(total) + " • " + subgroupDisplay());

        if (total <= 0) {
            addMessageCard("Пар не найдено", "Для выбранной подгруппы на этой неделе пары не найдены. Можно переключиться на «Все подгруппы».");
            return;
        }

        for (DaySchedule day : result.days) {
            List<String> visiblePairs = filterPairsForSubgroup(day.pairs);
            if (!visiblePairs.isEmpty()) {
                addDayBlock(day, visiblePairs);
            }
        }
    }

    private List<String> filterPairsForSubgroup(List<String> pairs) {
        List<String> visible = new ArrayList<>();
        if (SUBGROUP_ALL.equals(selectedSubgroup)) {
            visible.addAll(pairs);
            return visible;
        }
        for (String pair : pairs) {
            String filtered = filterPairForSubgroup(pair, selectedSubgroup);
            if (!filtered.isEmpty()) {
                visible.add(filtered);
            }
        }
        return visible;
    }

    private String filterPairForSubgroup(String pair, String subgroup) {
        String[] parts = pair.split("\n");
        if (parts.length <= 1) return pair.trim();

        List<String> kept = new ArrayList<>();
        kept.add(parts[0].trim());

        boolean sawSubgroup = false;
        boolean keptSelected = false;
        boolean keepCurrentBlock = true;

        for (int i = 1; i < parts.length; i++) {
            String line = parts[i].trim();
            if (line.isEmpty()) continue;

            boolean startsLesson = isRawLessonLine(line);
            String lineSubgroup = extractSubgroupFromLine(line);

            if (startsLesson || lineSubgroup != null) {
                if (lineSubgroup != null) {
                    sawSubgroup = true;
                    keepCurrentBlock = subgroup.equals(lineSubgroup);
                    if (keepCurrentBlock) keptSelected = true;
                } else {
                    keepCurrentBlock = true;
                }
            }

            if (keepCurrentBlock) {
                kept.add(line);
            }
        }

        if (sawSubgroup && !keptSelected) return "";
        if (kept.size() <= 1) return "";

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < kept.size(); i++) {
            if (i > 0) builder.append('\n');
            builder.append(kept.get(i));
        }
        return builder.toString().trim();
    }

    private boolean isRawLessonLine(String line) {
        String lower = line.trim().toLowerCase(ru);
        return lower.startsWith("лек.") || lower.startsWith("пр.з.") || lower.startsWith("лаб.");
    }

    private String extractSubgroupFromLine(String line) {
        Matcher direct = Pattern.compile("(?i)\\b([12])\\s*подгруппа\\b").matcher(line);
        if (direct.find()) return direct.group(1);

        Matcher shortForm = Pattern.compile("(?i)п/г\\s*([12])\\b").matcher(line);
        if (shortForm.find()) return shortForm.group(1);

        return null;
    }

    private String summaryText(int pairCount, String label) {
        if (pairCount <= 0) return label + ": пары не найдены";
        if (pairCount == 1) return label + ": 1 пара";
        if (pairCount >= 2 && pairCount <= 4) return label + ": " + pairCount + " пары";
        return label + ": " + pairCount + " пар";
    }

    private String weekSummaryText(int pairCount) {
        if (pairCount <= 0) return "На неделе пары не найдены";
        if (pairCount == 1) return "На неделе 1 пара";
        if (pairCount >= 2 && pairCount <= 4) return "На неделе " + pairCount + " пары";
        return "На неделе " + pairCount + " пар";
    }

    private void loadCachedSchedule() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String cached = prefs.getString(cacheKey(KEY_TEXT), "");
        if (!cached.isEmpty()) {
            String date = prefs.getString(cacheKey(KEY_DATE), todayString());
            ScheduleResult result = parseSchedule(date, cached);
            showSchedule(result, true);
            String updated = prefs.getString(cacheKey(KEY_UPDATED), "");
            statusView.setText(updated.isEmpty() ? "Показана сохранённая версия." : "Показана сохранённая версия от " + updated + ".");
        }
    }

    private void saveCache(String date, String text) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(cacheKey(KEY_DATE), date)
                .putString(cacheKey(KEY_TEXT), text)
                .putString(cacheKey(KEY_UPDATED), nowString())
                .apply();
    }

    private void showLoadFailure(String message) {
        setLoading(false);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String cached = prefs.getString(cacheKey(KEY_TEXT), "");
        String date = prefs.getString(cacheKey(KEY_DATE), todayString());
        String updated = prefs.getString(cacheKey(KEY_UPDATED), "");

        if (!cached.isEmpty()) {
            ScheduleResult cachedResult = parseSchedule(date, cached);
            showSchedule(cachedResult, true);
            statusView.setText("Не удалось обновить. Показана сохранённая версия" + (updated.isEmpty() ? "." : " от " + updated + "."));
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } else {
            listLayout.removeAllViews();
            summaryView.setText("Расписание не загрузилось");
            addMessageCard("Не удалось загрузить расписание", message + "\n\nНажми «Открыть сайт», чтобы открыть расписание вручную.");
            statusView.setText("Ошибка загрузки");
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(!loading);
        refreshButton.setAlpha(loading ? 0.65f : 1f);
    }

    private void addDayBlock(DaySchedule day, List<String> pairs) {
        LinearLayout dayCard = new LinearLayout(this);
        dayCard.setOrientation(LinearLayout.VERTICAL);
        dayCard.setPadding(dp(12), dp(12), dp(12), dp(8));
        dayCard.setBackground(cardBackground(cardSurface));
        dayCard.setElevation(dp(2));

        LinearLayout dayHeader = new LinearLayout(this);
        dayHeader.setOrientation(LinearLayout.HORIZONTAL);
        dayHeader.setGravity(Gravity.CENTER_VERTICAL);
        dayCard.addView(dayHeader);

        TextView dayTitle = new TextView(this);
        dayTitle.setText(day.dayName);
        dayTitle.setTextColor(textMain);
        dayTitle.setTextSize(18);
        dayTitle.setTypeface(Typeface.DEFAULT_BOLD);
        dayHeader.addView(dayTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView dayDate = new TextView(this);
        dayDate.setText(day.displayDate);
        dayDate.setTextColor(greenDark);
        dayDate.setTextSize(14);
        dayDate.setTypeface(Typeface.DEFAULT_BOLD);
        dayDate.setPadding(dp(10), dp(4), dp(10), dp(4));
        dayDate.setBackground(pillBackground(greenSoft, border));
        dayHeader.addView(dayDate);

        for (String pair : pairs) {
            addCompactPairCard(dayCard, pair);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        listLayout.addView(dayCard, params);
    }

    private void addCompactPairCard(LinearLayout parent, String text) {
        ParsedPair parsed = parsePairCard(text);

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(10), dp(9), dp(10), dp(9));
        item.setBackground(locationBackground(darkMode ? Color.rgb(32, 39, 34) : Color.rgb(248, 251, 248), border));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        item.addView(topRow);

        TextView pairName = new TextView(this);
        pairName.setText(parsed.pairName);
        pairName.setTextColor(greenDark);
        pairName.setTextSize(13);
        pairName.setTypeface(Typeface.DEFAULT_BOLD);
        pairName.setPadding(dp(9), dp(3), dp(9), dp(3));
        pairName.setBackground(pillBackground(greenSoft, border));
        topRow.addView(pairName);

        TextView time = new TextView(this);
        time.setText(parsed.time);
        time.setTextColor(textMain);
        time.setTextSize(15);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        timeParams.setMargins(dp(10), 0, 0, 0);
        topRow.addView(time, timeParams);

        for (String line : parsed.lines) {
            String pretty = prettifyLessonLine(line);
            if (pretty.startsWith("Аудитория:")) {
                addCompactLocationLine(item, pretty);
                continue;
            }
            boolean important = isLessonLine(pretty);
            int color = pretty.startsWith("Преподаватель:") ? textMuted : textMain;
            addBodyLine(item, pretty, important, color);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, 0);
        parent.addView(item, params);
    }

    private void addCompactLocationLine(LinearLayout parent, String line) {
        String place = line.replaceFirst("(?i)^Аудитория:\\s*", "").trim();
        TextView view = new TextView(this);
        view.setText("📍 " + place.replace("учебный корпус", "корпус"));
        view.setTextColor(greenDark);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(dp(10), dp(6), dp(10), dp(6));
        view.setBackground(pillBackground(greenSoft, border));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        parent.addView(view, params);
    }

    private void addPairCard(String text) {
        ParsedPair parsed = parsePairCard(text);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setBackground(cardBackground(cardSurface));
        outer.setElevation(dp(2));
        outer.setClipToOutline(false);

        View stripe = new View(this);
        stripe.setBackgroundColor(green);
        LinearLayout.LayoutParams stripeParams = new LinearLayout.LayoutParams(dp(5), LinearLayout.LayoutParams.MATCH_PARENT);
        outer.addView(stripe, stripeParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(13), dp(14), dp(13));
        outer.addView(content, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(topRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView pairChip = new TextView(this);
        pairChip.setText(parsed.pairName);
        pairChip.setTextColor(greenDark);
        pairChip.setTextSize(14);
        pairChip.setTypeface(Typeface.DEFAULT_BOLD);
        pairChip.setPadding(dp(10), dp(4), dp(10), dp(4));
        pairChip.setBackground(pillBackground(greenSoft, Color.rgb(201, 228, 205)));
        topRow.addView(pairChip, wrapParams());

        TextView timeView = new TextView(this);
        timeView.setText(parsed.time);
        timeView.setTextColor(textMain);
        timeView.setTextSize(16);
        timeView.setTypeface(Typeface.DEFAULT_BOLD);
        timeView.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        timeParams.setMargins(dp(10), 0, 0, 0);
        topRow.addView(timeView, timeParams);

        if (parsed.lines.isEmpty()) {
            addBodyLine(content, "Пары не найдены", true, textMuted);
        } else {
            for (String line : parsed.lines) {
                String pretty = prettifyLessonLine(line);
                if (pretty.startsWith("Аудитория:")) {
                    addLocationBlock(content, pretty);
                    continue;
                }
                boolean important = isLessonLine(pretty);
                int color = textMain;
                if (pretty.startsWith("Преподаватель:")) color = textMuted;
                addBodyLine(content, pretty, important, color);
            }
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        listLayout.addView(outer, params);
    }

    private void addBodyLine(LinearLayout parent, String text, boolean important, int color) {
        TextView lineView = new TextView(this);
        lineView.setText(text);
        lineView.setTextColor(color);
        lineView.setTextSize(important ? 16 : 14);
        lineView.setTypeface(important ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        lineView.setLineSpacing(dp(1), 1.0f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        parent.addView(lineView, params);
    }

    private void addLocationBlock(LinearLayout parent, String line) {
        String place = line.replaceFirst("(?i)^Аудитория:\\s*", "").trim();
        String room = place;
        String building = "";

        Matcher matcher = Pattern.compile("(?i)^(.*?)(?:,\\s*)?(учебный\\s+корпус\\s*№?\\s*\\d+.*)$").matcher(place);
        if (matcher.find()) {
            room = matcher.group(1).replaceAll(",\\s*$", "").trim();
            building = matcher.group(2).trim();
        }

        if (room.toLowerCase(ru).startsWith("учебный корпус")) {
            building = room;
            room = "";
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        boxParams.setMargins(0, dp(12), 0, 0);
        parent.addView(box, boxParams);

        if (!room.isEmpty()) {
            addLocationCard(box, "Аудитория", room, roomChipBg, roomChipStroke,
                    darkMode ? Color.rgb(183, 230, 194) : Color.rgb(60, 116, 73),
                    darkMode ? Color.rgb(245, 252, 246) : Color.rgb(31, 69, 41));
        }
        if (!building.isEmpty()) {
            addLocationCard(box, "Корпус", formatBuildingValue(building), buildingChipBg, buildingChipStroke,
                    darkMode ? Color.rgb(195, 241, 205) : Color.rgb(45, 106, 62),
                    buildingChipText);
        }
    }

    private void addLocationCard(LinearLayout parent, String label, String value, int bgColor, int strokeColor, int labelColor, int valueColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(9), dp(12), dp(9));
        card.setBackground(locationBackground(bgColor, strokeColor));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(labelColor);
        labelView.setTextSize(12);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(dp(10), dp(5), dp(10), dp(5));
        labelView.setBackground(pillBackground(darkMode
                ? Color.argb(58, 183, 230, 194)
                : Color.argb(72, 61, 137, 79), Color.TRANSPARENT));
        card.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(valueColor);
        valueView.setTextSize(18);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setSingleLine(false);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        valueParams.setMargins(dp(12), 0, 0, 0);
        card.addView(valueView, valueParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        parent.addView(card, params);
    }

    private GradientDrawable locationBackground(int color, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private String formatBuildingValue(String building) {
        String value = building.replaceAll("(?i)учебный\\s+корпус", "")
                .replaceAll("№\\s+", "№")
                .replaceAll("\\s+", " ")
                .replaceAll("^,\\s*", "")
                .trim();
        if (value.isEmpty()) {
            return building.replaceAll("\\s+", " ").trim();
        }
        return value;
    }

    private ParsedPair parsePairCard(String text) {
        String[] parts = text.split("\\n");
        String first = parts.length > 0 ? parts[0].trim() : "Пара";
        String pairName = first;
        String time = "";
        if (first.contains("•")) {
            String[] head = first.split("•", 2);
            pairName = head[0].trim();
            time = head[1].trim();
        }
        List<String> lines = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String line = parts[i].trim();
            if (!line.isEmpty()) lines.add(line);
        }
        return new ParsedPair(pairName, time, lines);
    }

    private String prettifyLessonLine(String line) {
        String clean = line.trim().replaceAll("\\s+", " ");
        clean = clean.replace("Преп.:", "Преподаватель:");
        clean = clean.replace("Ауд.:", "Аудитория:");
        clean = clean.replace("Аудитория:", "Аудитория: ").replaceAll("Аудитория:\\s+", "Аудитория: ");
        clean = clean.replace("Преподаватель:", "Преподаватель: ").replaceAll("Преподаватель:\\s+", "Преподаватель: ");

        Matcher practice = Pattern.compile("(?i)^(пр\\.з\\.|лаб\\.)\\s*\\(п/г\\s*([^)]*)\\)\\s*(.+)$").matcher(clean);
        if (practice.find()) {
            String type = practice.group(1).toLowerCase(ru).startsWith("лаб") ? "Лабораторная" : "Практика";
            String group = practice.group(2).replaceAll("\\s+", " ").trim();
            String subject = practice.group(3).trim();
            return type + " • " + group + " • " + subject;
        }

        Matcher lecture = Pattern.compile("(?i)^лек\\.\\s*(.+)$").matcher(clean);
        if (lecture.find()) {
            return "Лекция • " + lecture.group(1).trim();
        }
        return clean;
    }

    private boolean isLessonLine(String line) {
        return line.startsWith("Лекция") || line.startsWith("Практика") || line.startsWith("Лабораторная") ||
                (!line.startsWith("Преподаватель:") && !line.startsWith("Аудитория:"));
    }

    private void addMessageCard(String title, String message) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(cardBackground(cardSurface));
        card.setElevation(dp(2));

        TextView titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextColor(textMain);
        titleText.setTextSize(17);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(titleText);

        TextView messageText = new TextView(this);
        messageText.setText(message);
        messageText.setTextColor(textMuted);
        messageText.setTextSize(15);
        messageText.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.setMargins(0, dp(8), 0, 0);
        card.addView(messageText, msgParams);

        listLayout.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }


    private void registerUpdateDownloadReceiver() {
        updateDownloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (id != updateDownloadId) return;
                handleDownloadedUpdate(id);
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(updateDownloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateDownloadReceiver, filter);
        }
    }

    private void checkForUpdates(boolean manual) {
        String owner = getString(R.string.github_owner).trim();
        String repo = getString(R.string.github_repo).trim();

        if (owner.isEmpty() || PLACEHOLDER_OWNER.equals(owner) || repo.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("GitHub ещё не настроен")
                    .setMessage("Создай репозиторий на GitHub, потом в файле res/values/strings.xml замени github_owner на свой ник GitHub, а github_repo на название репозитория.")
                    .setPositiveButton("Понятно", null)
                    .show();
            return;
        }

        if (updateButton != null) {
            updateButton.setEnabled(false);
            updateButton.setText("Проверка...");
        }

        new Thread(() -> {
            try {
                UpdateInfo updateInfo = fetchLatestRelease(owner, repo);
                main.post(() -> showUpdateResult(updateInfo, manual));
            } catch (Exception e) {
                main.post(() -> {
                    restoreUpdateButton();
                    if (manual) {
                        new AlertDialog.Builder(this)
                                .setTitle("Не удалось проверить обновления")
                                .setMessage(e.getMessage() == null ? "Ошибка сети или GitHub API." : e.getMessage())
                                .setPositiveButton("ОК", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    private UpdateInfo fetchLatestRelease(String owner, String repo) throws Exception {
        URL url = new URL("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "BelGUScheduleApp");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);

        int code = connection.getResponseCode();
        if (code == 404) {
            throw new Exception("Релизов не найдено. На GitHub нужно создать Release и прикрепить APK.");
        }
        if (code < 200 || code >= 300) {
            throw new Exception("GitHub вернул ошибку: HTTP " + code);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();

        JSONObject release = new JSONObject(builder.toString());
        String tag = release.optString("tag_name", "");
        String name = release.optString("name", tag);
        String body = release.optString("body", "");
        String htmlUrl = release.optString("html_url", "");
        String apkUrl = "";

        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String assetName = asset.optString("name", "").toLowerCase(ru);
                String downloadUrl = asset.optString("browser_download_url", "");
                if (assetName.endsWith(".apk") && !downloadUrl.isEmpty()) {
                    apkUrl = downloadUrl;
                    break;
                }
            }
        }

        return new UpdateInfo(tag, name, body, htmlUrl, apkUrl);
    }

    private void showUpdateResult(UpdateInfo updateInfo, boolean manual) {
        restoreUpdateButton();

        String currentVersion = currentVersionName();
        boolean hasUpdate = compareVersions(updateInfo.tagName, currentVersion) > 0;

        if (!hasUpdate) {
            if (manual) {
                new AlertDialog.Builder(this)
                        .setTitle("Обновлений нет")
                        .setMessage("Установлена версия " + currentVersion + ". Последний релиз на GitHub: " + updateInfo.tagName + ".")
                        .setPositiveButton("ОК", null)
                        .show();
            }
            return;
        }

        String message = "Установлена: " + currentVersion + "\n" +
                "Доступна: " + updateInfo.tagName + "\n\n" +
                trimReleaseBody(updateInfo.body);

        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("Доступно обновление")
                .setMessage(message)
                .setNegativeButton("Позже", null)
                .setNeutralButton("GitHub", (d, which) -> openUrl(updateInfo.htmlUrl));

        if (!updateInfo.apkUrl.isEmpty()) {
            dialog.setPositiveButton("Скачать APK", (d, which) -> downloadUpdateApk(updateInfo.apkUrl, updateInfo.tagName));
        } else {
            dialog.setPositiveButton("Открыть релиз", (d, which) -> openUrl(updateInfo.htmlUrl));
        }

        dialog.show();
    }

    private void restoreUpdateButton() {
        if (updateButton != null) {
            updateButton.setEnabled(true);
            updateButton.setText("Обновления");
        }
    }

    private void downloadUpdateApk(String apkUrl, String tagName) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("Нужно разрешение")
                    .setMessage("Чтобы приложение могло открыть установщик APK, разреши установку из этого источника. После этого снова нажми «Обновления».")
                    .setPositiveButton("Открыть настройки", (d, which) -> openUnknownSourcesSettings())
                    .setNegativeButton("Отмена", null)
                    .show();
            return;
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("Расписание БелГУ " + tagName);
            request.setDescription("Скачиваю обновление APK");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS,
                    "BelGUScheduleApp-" + cleanFileName(tagName) + ".apk");

            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) throw new Exception("DownloadManager недоступен");
            updateDownloadId = manager.enqueue(request);
            Toast.makeText(this, "Скачиваю обновление...", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("Не удалось скачать APK")
                    .setMessage(e.getMessage() == null ? "Ошибка загрузки." : e.getMessage())
                    .setPositiveButton("ОК", null)
                    .show();
        }
    }

    private void handleDownloadedUpdate(long id) {
        try {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) return;

            DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
            Cursor cursor = manager.query(query);
            if (cursor == null) return;

            try {
                if (!cursor.moveToFirst()) return;
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(statusIndex);
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Uri apkUri = manager.getUriForDownloadedFile(id);
                    if (apkUri != null) openApkInstaller(apkUri);
                } else {
                    Toast.makeText(this, "Обновление не скачалось", Toast.LENGTH_LONG).show();
                }
            } finally {
                cursor.close();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть установщик APK", Toast.LENGTH_LONG).show();
        }
    }

    private void openApkInstaller(Uri apkUri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Открой скачанный APK из уведомления загрузки", Toast.LENGTH_LONG).show();
        }
    }

    private void openUnknownSourcesSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            startActivity(intent);
        }
    }

    private void openUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "Ссылка пустая", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show();
        }
    }

    private String currentVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "0" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0";
        }
    }

    private int compareVersions(String latest, String current) {
        int[] a = parseVersionNumbers(latest);
        int[] b = parseVersionNumbers(current);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return av - bv;
        }
        return 0;
    }

    private int[] parseVersionNumbers(String version) {
        String clean = version == null ? "" : version.replaceFirst("^[vV]", "");
        String[] parts = clean.split("\\.");
        ArrayList<Integer> values = new ArrayList<>();
        for (String part : parts) {
            String digits = part.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    values.add(Integer.parseInt(digits));
                } catch (Exception ignored) {
                }
            }
        }
        if (values.isEmpty()) values.add(0);

        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    private String trimReleaseBody(String body) {
        if (body == null || body.trim().isEmpty()) return "Описание релиза пустое.";
        String clean = body.trim();
        if (clean.length() > 500) clean = clean.substring(0, 500) + "...";
        return clean;
    }

    private String cleanFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "update";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private GradientDrawable headerBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{headerStart, headerEnd});
        drawable.setCornerRadii(new float[]{0, 0, 0, 0, 0, 0, 0, 0});
        return drawable;
    }

    private GradientDrawable buttonBackground(int color, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(13));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable pillBackground(int color, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(99));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable cardBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(16));
        drawable.setStroke(dp(1), border);
        return drawable;
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private int currentDayIndex() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return (day + 5) % 7; // Monday = 0, Sunday = 6
    }

    private Calendar calendarForSelectedDay() {
        Calendar calendar = Calendar.getInstance(ru);
        int currentIndex = currentDayIndex();
        calendar.add(Calendar.DAY_OF_MONTH, selectedDayIndex - currentIndex);
        return calendar;
    }

    private String selectedDateString() {
        return new SimpleDateFormat("dd.MM.yyyy", ru).format(calendarForSelectedDay().getTime());
    }

    private String dateForDayIndex(int dayIndex) {
        int oldIndex = selectedDayIndex;
        selectedDayIndex = dayIndex;
        String date = selectedDateString();
        selectedDayIndex = oldIndex;
        return date;
    }

    private String selectedDayNameShort() {
        String[] names = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};
        if (selectedDayIndex == currentDayIndex()) return "Сегодня";
        if (selectedDayIndex >= 0 && selectedDayIndex < names.length) return names[selectedDayIndex];
        return "День";
    }

    private String todayString() {
        return new SimpleDateFormat("dd.MM.yyyy", ru).format(Calendar.getInstance().getTime());
    }

    private String nowString() {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", ru).format(Calendar.getInstance().getTime());
    }

    private static class DaySchedule {
        final String displayDate;
        final String dayName;
        final String fullText;
        final List<String> pairs;

        DaySchedule(String displayDate, String dayName, String fullText, List<String> pairs) {
            this.displayDate = displayDate;
            this.dayName = dayName;
            this.fullText = fullText;
            this.pairs = pairs;
        }
    }

    private static class ScheduleResult {
        final String displayDate;
        final String fullText;
        final List<String> pairs;
        final List<DaySchedule> days;

        ScheduleResult(String displayDate, String fullText, List<String> pairs, List<DaySchedule> days) {
            this.displayDate = displayDate;
            this.fullText = fullText;
            this.pairs = pairs;
            this.days = days;
        }
    }

    private static class UpdateInfo {
        final String tagName;
        final String name;
        final String body;
        final String htmlUrl;
        final String apkUrl;

        UpdateInfo(String tagName, String name, String body, String htmlUrl, String apkUrl) {
            this.tagName = tagName;
            this.name = name;
            this.body = body;
            this.htmlUrl = htmlUrl;
            this.apkUrl = apkUrl;
        }
    }

    private static class ParsedPair {
        final String pairName;
        final String time;
        final List<String> lines;

        ParsedPair(String pairName, String time, List<String> lines) {
            this.pairName = pairName;
            this.time = time;
            this.lines = lines;
        }
    }
}
