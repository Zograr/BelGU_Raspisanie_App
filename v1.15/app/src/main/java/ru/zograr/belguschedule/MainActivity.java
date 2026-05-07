package ru.zograr.belguschedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.TimePickerDialog;
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
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    private static final String KEY_SHOW_WEEK = "show_whole_week_v2";
    private static final String DEFAULT_GROUP = "90002595";
    private static final String PLACEHOLDER_OWNER = "YOUR_GITHUB_USERNAME";
    private static final String SCHEDULE_BASE_URL = "https://bsuedu.ru/bsu/education/schedule/groups/index.php?group=";
    private static final String TEACHERS_URL = "https://bsuedu.ru/bsu/education/schedule/teachers/";
    private static final String KEY_AUTO_UPDATE_HOUR = "auto_update_hour";
    private static final String KEY_AUTO_UPDATE_MINUTE = "auto_update_minute";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_UPDATE_DOWNLOAD_ID = "update_download_id";
    private static final String KEY_UPDATE_DOWNLOAD_TAG = "update_download_tag";
    private static final String KEY_UPDATE_DOWNLOAD_URL = "update_download_url";
    private static final String SUBGROUP_ALL = "all";
    private static final String SUBGROUP_1 = "1";
    private static final String SUBGROUP_2 = "2";
    private static final int MAX_EXTRACT_ATTEMPTS = 4;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Locale ru = new Locale("ru", "RU");

    private FrameLayout appFrame;
    private LinearLayout rootLayout;
    private LinearLayout headerLayout;
    private LinearLayout controlsLayout;
    private LinearLayout listLayout;
    private LinearLayout settingsPanel;
    private LinearLayout settingsPanelContent;
    private LinearLayout themePanel;
    private LinearLayout themePanelContent;
    private LinearLayout teachersPanel;
    private LinearLayout teachersPanelContent;
    private LinearLayout easterEggPanel;
    private TextView statusView;
    private TextView groupPill;
    private TextView subgroupPill;
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
    private String themeMode = "green_light";
    private ScheduleResult currentResult;
    private boolean mainFrameError = false;
    private long updateDownloadId = -1L;
    private BroadcastReceiver updateDownloadReceiver;
    private boolean settingsOpen = false;
    private boolean themePanelOpen = false;
    private boolean teachersPanelOpen = false;
    private boolean teacherScheduleOpen = false;
    private boolean easterEggOpen = false;
    private int versionTapCount = 0;
    private String themeFilter = "light";
    private final List<TeacherItem> teacherItems = new ArrayList<>();

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
        themeMode = prefs.getString(KEY_THEME_MODE, darkMode ? "green_dark" : "green_light");
        darkMode = themeMode.endsWith("_dark");
        updateDownloadId = prefs.getLong(KEY_UPDATE_DOWNLOAD_ID, -1L);
        applyPalette();
        buildUi();
        setupWebView();
        registerUpdateDownloadReceiver();
        DailyUpdateReceiver.scheduleDailyUpdate(this);
        ScheduleWidgetProvider.updateAllWidgets(this);
        loadCachedSchedule();
        refreshSchedule();
    }

    @Override
    public void onBackPressed() {
        if (easterEggOpen) {
            hideEasterEgg();
            return;
        }
        if (themePanelOpen) {
            hideThemePanel();
            return;
        }
        if (teachersPanelOpen) {
            if (teacherScheduleOpen) {
                teacherScheduleOpen = false;
                refreshTeachersPanelContent("");
            } else {
                hideTeachersPanel();
            }
            return;
        }
        if (settingsOpen) {
            hideSettingsPanel();
            return;
        }
        super.onBackPressed();
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

        appFrame = new FrameLayout(this);
        setContentView(appFrame);

        LinearLayout root = new LinearLayout(this);
        rootLayout = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);
        appFrame.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

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
        headerTop.addView(groupPill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        subgroupPill = new TextView(this);
        subgroupPill.setTextColor(Color.rgb(225, 247, 228));
        subgroupPill.setTextSize(13);
        subgroupPill.setTypeface(Typeface.DEFAULT_BOLD);
        subgroupPill.setPadding(dp(12), dp(5), dp(12), dp(5));
        subgroupPill.setGravity(Gravity.CENTER);
        subgroupPill.setBackground(pillBackground(Color.argb(48, 255, 255, 255), Color.argb(70, 255, 255, 255)));
        LinearLayout.LayoutParams subgroupParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subgroupParams.setMargins(dp(8), 0, 0, 0);
        headerTop.addView(subgroupPill, subgroupParams);

        settingsButton = new TextView(this);
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setText("⚙");
        settingsButton.setTextSize(22);
        settingsButton.setTypeface(Typeface.DEFAULT_BOLD);
        settingsButton.setTextColor(Color.WHITE);
        settingsButton.setBackground(pillBackground(Color.argb(52, 255, 255, 255), Color.argb(90, 255, 255, 255)));
        settingsButton.setOnClickListener(v -> showSettingsPanel());
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
            ScheduleWidgetProvider.updateAllWidgets(this);
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



    private void showSettingsPanel() {
        if (settingsPanel == null) {
            buildSettingsPanel();
        }
        refreshSettingsPanelContent();
        settingsPanel.setVisibility(View.VISIBLE);
        settingsPanel.setTranslationX(getResources().getDisplayMetrics().widthPixels);
        settingsOpen = true;
        settingsPanel.animate()
                .translationX(0)
                .setDuration(220)
                .start();
    }

    private void hideSettingsPanel() {
        if (settingsPanel == null) return;
        settingsOpen = false;
        settingsPanel.animate()
                .translationX(getResources().getDisplayMetrics().widthPixels)
                .setDuration(180)
                .withEndAction(() -> settingsPanel.setVisibility(View.GONE))
                .start();
    }

    private void buildSettingsPanel() {
        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setBackgroundColor(background);
        settingsPanel.setPadding(dp(14), getStatusBarHeight() + dp(8), dp(14), dp(14));
        settingsPanel.setVisibility(View.GONE);
        settingsPanel.setClickable(true);
        settingsPanel.setFocusable(true);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        appFrame.addView(settingsPanel, panelParams);
    }

    private void refreshSettingsPanelContent() {
        if (settingsPanel == null) return;

        settingsPanel.removeAllViews();
        settingsPanel.setBackgroundColor(background);
        settingsPanel.setPadding(dp(14), getStatusBarHeight() + dp(8), dp(14), dp(14));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        settingsPanel.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Настройки");
        title.setTextColor(textMain);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextColor(textMain);
        close.setTextSize(26);
        close.setGravity(Gravity.CENTER);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setBackground(pillBackground(greenSoft, border));
        close.setOnClickListener(v -> hideSettingsPanel());
        top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView info = new TextView(this);
        info.setText("Группа " + scheduleGroup + " • " + subgroupDisplay() + "\n" +
                "Режим: " + (showWholeWeek ? "вся неделя" : "сегодня"));
        info.setTextColor(textMuted);
        info.setTextSize(14);
        info.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoParams.setMargins(0, dp(8), 0, dp(14));
        settingsPanel.addView(info, infoParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        settingsPanel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        settingsPanelContent = new LinearLayout(this);
        settingsPanelContent.setOrientation(LinearLayout.VERTICAL);
        settingsPanelContent.setPadding(0, 0, 0, dp(20));
        scroll.addView(settingsPanelContent);

        addSettingsSection("Расписание");
        addModeSwitch();
        addSubgroupChooser();
        addGroupChooser();
        addTeachersScheduleButton();

        addSettingsSection("Приложение");
        addThemeButton();
        addAutoUpdateTimeButton();
        addUpdateButton();

        addSettingsVersionLabelToBottom();
    }

    private void addSettingsSection(String title) {
        TextView section = new TextView(this);
        section.setText(title);
        section.setTextColor(greenDark);
        section.setTextSize(15);
        section.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(12), 0, dp(8));
        settingsPanelContent.addView(section, params);
    }

    private void addModeSwitch() {
        LinearLayout card = settingsCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(textBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView title = new TextView(this);
        title.setText("Показ расписания");
        title.setTextColor(textMain);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        textBox.addView(title);

        TextView sub = new TextView(this);
        sub.setText(showWholeWeek ? "Сейчас показываются все пары на неделе" : "Сейчас показываются только пары на сегодня");
        sub.setTextColor(textMuted);
        sub.setTextSize(13);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.setMargins(0, dp(4), 0, 0);
        textBox.addView(sub, subParams);

        TextView toggle = new TextView(this);
        toggle.setText(showWholeWeek ? "Неделя" : "Сегодня");
        toggle.setTextColor(showWholeWeek ? Color.WHITE : greenDark);
        toggle.setTextSize(13);
        toggle.setTypeface(Typeface.DEFAULT_BOLD);
        toggle.setGravity(Gravity.CENTER);
        toggle.setPadding(dp(14), dp(7), dp(14), dp(7));
        toggle.setBackground(pillBackground(showWholeWeek ? green : greenSoft, showWholeWeek ? green : border));
        card.addView(toggle);

        View.OnClickListener listener = v -> {
            showWholeWeek = !showWholeWeek;
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_SHOW_WEEK, showWholeWeek)
                    .apply();
            if (currentResult != null) showSchedule(currentResult, true);
            refreshSettingsPanelContent();
        };
        card.setOnClickListener(listener);
        toggle.setOnClickListener(listener);

        addSettingsCard(card);
    }

    private void addSubgroupChooser() {
        LinearLayout card = settingsCard();
        card.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Подгруппа");
        title.setTextColor(textMain);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(10), 0, 0);
        card.addView(row, rowParams);

        addSubgroupSegment(row, "1", SUBGROUP_1);
        addSubgroupSegment(row, "2", SUBGROUP_2);
        addSubgroupSegment(row, "Все", SUBGROUP_ALL);

        addSettingsCard(card);
    }

    private void addGroupChooser() {
        LinearLayout card = settingsButtonCard("Группа " + scheduleGroup,
                "Нажми, чтобы выбрать или вписать другую группу");
        card.setOnClickListener(v -> showGroupDialog());
        addSettingsCard(card);
    }

    private void addTeachersScheduleButton() {
        LinearLayout card = settingsButtonCard("Расписание преподавателей",
                "Список преподавателей, поиск и просмотр расписания");
        card.setOnClickListener(v -> showTeachersPanel());
        addSettingsCard(card);
    }

    private void addAutoUpdateTimeButton() {
        LinearLayout card = settingsButtonCard("Автозагрузка расписания",
                "Каждый день в " + autoUpdateTimeText());
        card.setOnClickListener(v -> showAutoUpdateTimeDialog());
        addSettingsCard(card);
    }

    private String autoUpdateTimeText() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int hour = prefs.getInt(KEY_AUTO_UPDATE_HOUR, 7);
        int minute = prefs.getInt(KEY_AUTO_UPDATE_MINUTE, 0);
        return String.format(ru, "%02d:%02d", hour, minute);
    }

    private void showAutoUpdateTimeDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int hour = prefs.getInt(KEY_AUTO_UPDATE_HOUR, 7);
        int minute = prefs.getInt(KEY_AUTO_UPDATE_MINUTE, 0);

        TimePickerDialog dialog = new TimePickerDialog(this, (view, selectedHour, selectedMinute) -> {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_AUTO_UPDATE_HOUR, selectedHour)
                    .putInt(KEY_AUTO_UPDATE_MINUTE, selectedMinute)
                    .apply();
            DailyUpdateReceiver.scheduleDailyUpdate(this);
            Toast.makeText(this, "Автозагрузка: " + String.format(ru, "%02d:%02d", selectedHour, selectedMinute), Toast.LENGTH_SHORT).show();
            if (settingsOpen) refreshSettingsPanelContent();
        }, hour, minute, true);
        dialog.setTitle("Время автозагрузки");
        dialog.show();
    }

    private void addSubgroupSegment(LinearLayout row, String label, String value) {
        TextView segment = new TextView(this);
        boolean active = value.equals(selectedSubgroup);
        segment.setText(label);
        segment.setTextColor(active ? Color.WHITE : greenDark);
        segment.setTextSize(14);
        segment.setTypeface(Typeface.DEFAULT_BOLD);
        segment.setGravity(Gravity.CENTER);
        segment.setPadding(dp(10), dp(9), dp(10), dp(9));
        segment.setBackground(pillBackground(active ? green : greenSoft, active ? green : border));
        segment.setOnClickListener(v -> {
            selectSubgroup(value);
            refreshSettingsPanelContent();
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        row.addView(segment, params);
    }

    private void addThemeButton() {
        LinearLayout card = settingsButtonCard("Темы оформления",
                "Сейчас: " + themeDisplayName(themeMode));
        card.setOnClickListener(v -> showThemePanel());
        addSettingsCard(card);
    }

    private void showThemePanel() {
        if (themePanel == null) {
            buildThemePanel();
        }
        themeFilter = darkMode ? "dark" : "light";
        refreshThemePanelContent();
        themePanel.setVisibility(View.VISIBLE);
        themePanel.setTranslationX(getResources().getDisplayMetrics().widthPixels);
        themePanelOpen = true;
        themePanel.bringToFront();
        themePanel.animate()
                .translationX(0)
                .setDuration(220)
                .start();
    }

    private void hideThemePanel() {
        if (themePanel == null) return;
        themePanelOpen = false;
        themePanel.animate()
                .translationX(getResources().getDisplayMetrics().widthPixels)
                .setDuration(180)
                .withEndAction(() -> themePanel.setVisibility(View.GONE))
                .start();
    }

    private void buildThemePanel() {
        themePanel = new LinearLayout(this);
        themePanel.setOrientation(LinearLayout.VERTICAL);
        themePanel.setBackgroundColor(background);
        themePanel.setPadding(dp(14), getStatusBarHeight() + dp(8), dp(14), dp(14));
        themePanel.setVisibility(View.GONE);
        themePanel.setClickable(true);
        themePanel.setFocusable(true);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        appFrame.addView(themePanel, panelParams);
    }

    private void refreshThemePanelContent() {
        if (themePanel == null) return;

        themePanel.removeAllViews();
        themePanel.setBackgroundColor(background);
        themePanel.setPadding(dp(14), getStatusBarHeight() + dp(8), dp(14), dp(14));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        themePanel.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(textMain);
        back.setTextSize(30);
        back.setGravity(Gravity.CENTER);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setBackground(pillBackground(greenSoft, border));
        back.setOnClickListener(v -> hideThemePanel());
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = new TextView(this);
        title.setText("Темы");
        title.setTextColor(textMain);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(12), 0, 0, 0);
        top.addView(title, titleParams);

        TextView current = new TextView(this);
        current.setText("Сейчас: " + themeDisplayName(themeMode));
        current.setTextColor(textMuted);
        current.setTextSize(14);
        LinearLayout.LayoutParams currentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        currentParams.setMargins(0, dp(10), 0, dp(12));
        themePanel.addView(current, currentParams);

        LinearLayout filterCard = new LinearLayout(this);
        filterCard.setOrientation(LinearLayout.VERTICAL);
        filterCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        filterCard.setBackground(cardBackground(cardSurface));
        themePanel.addView(filterCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView filterTitle = new TextView(this);
        filterTitle.setText("Режим темы");
        filterTitle.setTextColor(textMain);
        filterTitle.setTextSize(16);
        filterTitle.setTypeface(Typeface.DEFAULT_BOLD);
        filterCard.addView(filterTitle);

        LinearLayout segments = new LinearLayout(this);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams segParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        segParams.setMargins(0, dp(10), 0, 0);
        filterCard.addView(segments, segParams);

        addThemeFilterSegment(segments, "Светлые", "light");
        addThemeFilterSegment(segments, "Тёмные", "dark");

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        scrollParams.setMargins(0, dp(12), 0, 0);
        themePanel.addView(scroll, scrollParams);

        themePanelContent = new LinearLayout(this);
        themePanelContent.setOrientation(LinearLayout.VERTICAL);
        themePanelContent.setPadding(0, 0, 0, dp(20));
        scroll.addView(themePanelContent);

        if ("dark".equals(themeFilter)) {
            addThemeOptionCard("green_dark", "Зелёная тёмная", "спокойная тёмная тема");
            addThemeOptionCard("blue_dark", "Синяя тёмная", "ночной синий");
            addThemeOptionCard("purple_dark", "Фиолетовая тёмная", "контрастная тёмная");
            addThemeOptionCard("amber_dark", "Тёплая тёмная", "тёмная янтарная");
        } else {
            addThemeOptionCard("green_light", "Зелёная светлая", "классический светлый стиль");
            addThemeOptionCard("blue_light", "Синяя светлая", "спокойный учебный стиль");
            addThemeOptionCard("purple_light", "Фиолетовая светлая", "мягкий фиолетовый акцент");
            addThemeOptionCard("amber_light", "Тёплая светлая", "бежево-оранжевая");
        }
    }

    private void addThemeFilterSegment(LinearLayout row, String label, String value) {
        TextView segment = new TextView(this);
        boolean active = value.equals(themeFilter);
        segment.setText(label);
        segment.setTextColor(active ? Color.WHITE : greenDark);
        segment.setTextSize(14);
        segment.setTypeface(Typeface.DEFAULT_BOLD);
        segment.setGravity(Gravity.CENTER);
        segment.setPadding(dp(10), dp(9), dp(10), dp(9));
        segment.setBackground(pillBackground(active ? green : greenSoft, active ? green : border));
        segment.setOnClickListener(v -> {
            themeFilter = value;
            refreshThemePanelContent();
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        row.addView(segment, params);
    }

    private void addThemeOptionCard(String mode, String titleText, String subtitleText) {
        boolean active = mode.equals(themeMode);

        LinearLayout card = settingsCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(locationBackground(active ? greenSoft : cardSurface, active ? green : border));
        card.setOnClickListener(v -> setThemeMode(mode));

        TextView dot = new TextView(this);
        dot.setText(active ? "●" : "○");
        dot.setTextColor(active ? greenDark : textMuted);
        dot.setTextSize(21);
        dot.setGravity(Gravity.CENTER);
        card.addView(dot, new LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(textMain);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        textBox.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(textMuted);
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, dp(3), 0, 0);
        textBox.addView(subtitle, subtitleParams);

        card.addView(textBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView preview = new TextView(this);
        preview.setText("Aa");
        preview.setTextSize(14);
        preview.setTypeface(Typeface.DEFAULT_BOLD);
        preview.setTextColor(previewTextColor(mode));
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(12), dp(7), dp(12), dp(7));
        preview.setBackground(pillBackground(previewBgColor(mode), previewStrokeColor(mode)));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        previewParams.setMargins(dp(10), 0, 0, 0);
        card.addView(preview, previewParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        themePanelContent.addView(card, params);
    }

    private void setThemeMode(String mode) {
        themeMode = mode;
        darkMode = themeMode.endsWith("_dark");

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME_MODE, themeMode)
                .putBoolean(KEY_DARK_MODE, darkMode)
                .apply();

        applyPalette();
        applyThemeToViews();

        if (currentResult != null) {
            showSchedule(currentResult, true);
        }

        if (settingsPanel != null && settingsOpen) {
            settingsPanel.setAlpha(1f);
            settingsPanel.setBackgroundColor(background);
            settingsPanel.bringToFront();
            refreshSettingsPanelContent();
        }

        if (themePanel != null && themePanelOpen) {
            themePanel.setAlpha(1f);
            themePanel.setBackgroundColor(background);
            themePanel.bringToFront();
            refreshThemePanelContent();
        }
    }

    private String themeDisplayName(String mode) {
        switch (mode) {
            case "green_dark": return "Зелёная тёмная";
            case "blue_light": return "Синяя светлая";
            case "blue_dark": return "Синяя тёмная";
            case "purple_light": return "Фиолетовая светлая";
            case "purple_dark": return "Фиолетовая тёмная";
            case "amber_light": return "Тёплая светлая";
            case "amber_dark": return "Тёплая тёмная";
            default: return "Зелёная светлая";
        }
    }

    private int previewBgColor(String mode) {
        switch (mode) {
            case "green_dark": return Color.rgb(31, 48, 38);
            case "blue_light": return Color.rgb(224, 239, 252);
            case "blue_dark": return Color.rgb(24, 43, 63);
            case "purple_light": return Color.rgb(241, 232, 255);
            case "purple_dark": return Color.rgb(45, 34, 63);
            case "amber_light": return Color.rgb(252, 239, 213);
            case "amber_dark": return Color.rgb(58, 43, 24);
            default: return Color.rgb(225, 241, 229);
        }
    }

    private int previewStrokeColor(String mode) {
        switch (mode) {
            case "green_dark": return Color.rgb(75, 105, 82);
            case "blue_light": return Color.rgb(159, 195, 226);
            case "blue_dark": return Color.rgb(70, 106, 145);
            case "purple_light": return Color.rgb(202, 177, 235);
            case "purple_dark": return Color.rgb(103, 81, 139);
            case "amber_light": return Color.rgb(224, 188, 120);
            case "amber_dark": return Color.rgb(128, 95, 47);
            default: return Color.rgb(193, 215, 198);
        }
    }

    private int previewTextColor(String mode) {
        switch (mode) {
            case "green_dark": return Color.rgb(220, 255, 226);
            case "blue_light": return Color.rgb(24, 78, 125);
            case "blue_dark": return Color.rgb(220, 239, 255);
            case "purple_light": return Color.rgb(89, 52, 138);
            case "purple_dark": return Color.rgb(242, 231, 255);
            case "amber_light": return Color.rgb(117, 74, 10);
            case "amber_dark": return Color.rgb(255, 236, 190);
            default: return Color.rgb(29, 93, 47);
        }
    }

    private void addUpdateButton() {
        LinearLayout card = settingsButtonCard("Проверить обновления",
                "Проверка новой версии приложения на GitHub");
        card.setOnClickListener(v -> checkForUpdates(true));
        addSettingsCard(card);
    }

    private void addSettingsVersionLabelToBottom() {
        TextView version = new TextView(this);
        version.setText("v1.15");
        version.setTextColor(darkMode ? Color.argb(80, 236, 242, 235) : Color.argb(75, 28, 94, 47));
        version.setTextSize(13);
        version.setGravity(Gravity.CENTER);
        version.setTypeface(Typeface.DEFAULT_BOLD);
        version.setPadding(0, dp(6), 0, dp(4));
        version.setOnClickListener(v -> {
            versionTapCount++;
            if (versionTapCount >= 7) {
                versionTapCount = 0;
                showEasterEgg();
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(4), 0, dp(22));
        settingsPanel.addView(version, params);
    }

    private LinearLayout settingsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(cardBackground(cardSurface));
        card.setClickable(true);
        card.setFocusable(true);
        return card;
    }

    private LinearLayout settingsButtonCard(String titleText, String subtitleText) {
        LinearLayout card = settingsCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(textBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(textMain);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        textBox.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(textMuted);
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.setMargins(0, dp(4), 0, 0);
        textBox.addView(subtitle, subParams);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(greenDark);
        arrow.setTextSize(24);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(32), dp(44)));

        return card;
    }

    private void addSettingsCard(LinearLayout card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        settingsPanelContent.addView(card, params);
    }

    private void showTeachersPanel() {
        if (teachersPanel == null) {
            buildTeachersPanel();
        }
        teacherScheduleOpen = false;
        refreshTeachersPanelContent("");
        teachersPanel.setVisibility(View.VISIBLE);
        teachersPanel.setTranslationX(getResources().getDisplayMetrics().widthPixels);
        teachersPanelOpen = true;
        teachersPanel.bringToFront();
        teachersPanel.animate()
                .translationX(0)
                .setDuration(220)
                .start();

        if (teacherItems.isEmpty()) {
            loadTeacherList();
        }
    }

    private void hideTeachersPanel() {
        if (teachersPanel == null) return;
        teachersPanelOpen = false;
        teacherScheduleOpen = false;
        teachersPanel.animate()
                .translationX(getResources().getDisplayMetrics().widthPixels)
                .setDuration(180)
                .withEndAction(() -> teachersPanel.setVisibility(View.GONE))
                .start();
    }

    private void buildTeachersPanel() {
        teachersPanel = new LinearLayout(this);
        teachersPanel.setOrientation(LinearLayout.VERTICAL);
        teachersPanel.setBackgroundColor(background);
        teachersPanel.setPadding(dp(14), getStatusBarHeight() + dp(8), dp(14), dp(14));
        teachersPanel.setVisibility(View.GONE);
        teachersPanel.setClickable(true);
        teachersPanel.setFocusable(true);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        appFrame.addView(teachersPanel, panelParams);
    }

    private void refreshTeachersPanelContent(String query) {
        if (teachersPanel == null) return;

        teachersPanel.removeAllViews();
        teachersPanel.setBackgroundColor(background);
        teachersPanel.setPadding(dp(14), getStatusBarHeight() + dp(8), dp(14), dp(14));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        teachersPanel.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(textMain);
        back.setTextSize(30);
        back.setGravity(Gravity.CENTER);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setBackground(pillBackground(greenSoft, border));
        back.setOnClickListener(v -> {
            if (teacherScheduleOpen) {
                teacherScheduleOpen = false;
                refreshTeachersPanelContent("");
            } else {
                hideTeachersPanel();
            }
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = new TextView(this);
        title.setText(teacherScheduleOpen ? "Расписание" : "Преподаватели");
        title.setTextColor(textMain);
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(12), 0, 0, 0);
        top.addView(title, titleParams);

        if (!teacherScheduleOpen) {
            EditText search = new EditText(this);
            search.setSingleLine(true);
            search.setHint("Поиск преподавателя");
            search.setText(query);
            search.setTextColor(textMain);
            search.setHintTextColor(textMuted);
            search.setInputType(InputType.TYPE_CLASS_TEXT);
            search.setPadding(dp(12), dp(10), dp(12), dp(10));
            search.setBackground(locationBackground(cardSurface, border));
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            searchParams.setMargins(0, dp(12), 0, dp(12));
            teachersPanel.addView(search, searchParams);

            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    renderTeacherList(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        teachersPanel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        teachersPanelContent = new LinearLayout(this);
        teachersPanelContent.setOrientation(LinearLayout.VERTICAL);
        teachersPanelContent.setPadding(0, 0, 0, dp(20));
        scroll.addView(teachersPanelContent);

        if (!teacherScheduleOpen) {
            renderTeacherList(query);
        }
    }

    private void loadTeacherList() {
        if (teachersPanelContent != null) {
            teachersPanelContent.removeAllViews();
            addTeacherMessage("Загружаю список", "Получаю список преподавателей с сайта БелГУ...");
        }

        new Thread(() -> {
            try {
                List<TeacherItem> loaded = fetchTeacherList();
                main.post(() -> {
                    teacherItems.clear();
                    teacherItems.addAll(loaded);
                    if (teachersPanelOpen && !teacherScheduleOpen) {
                        refreshTeachersPanelContent("");
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (teachersPanelContent != null) {
                        teachersPanelContent.removeAllViews();
                        addTeacherMessage("Не удалось загрузить список", e.getMessage() == null ? "Ошибка сети или сайт не отдал список." : e.getMessage());
                    }
                });
            }
        }).start();
    }

    private List<TeacherItem> fetchTeacherList() throws Exception {
        String html = fetchUrl(TEACHERS_URL);
        List<TeacherItem> result = new ArrayList<>();

        Pattern optionPattern = Pattern.compile("(?is)<option[^>]+value=[\"']?(\\d+)[\"']?[^>]*>(.*?)</option>");
        Matcher matcher = optionPattern.matcher(html);
        while (matcher.find()) {
            String id = matcher.group(1).trim();
            String name = stripHtml(matcher.group(2)).replaceAll("\\s+", " ").trim();
            if (name.length() < 5) continue;
            if (!Pattern.compile("[А-Яа-яЁё]").matcher(name).find()) continue;
            if (name.toLowerCase(ru).contains("выберите")) continue;

            boolean exists = false;
            for (TeacherItem item : result) {
                if (item.id.equals(id)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) result.add(new TeacherItem(id, name));
        }

        java.util.Collections.sort(result, (a, b) -> a.name.compareToIgnoreCase(b.name));
        if (result.isEmpty()) {
            throw new Exception("Список преподавателей не найден. Возможно, сайт изменил форму расписания.");
        }
        return result;
    }

    private String fetchUrl(String urlText) throws Exception {
        URL url = new URL(urlText);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "BelGUScheduleApp");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new Exception("Сайт вернул HTTP " + code);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append('\n');
        }
        reader.close();
        return builder.toString();
    }

    private String stripHtml(String html) {
        return html.replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .trim();
    }

    private void renderTeacherList(String query) {
        if (teachersPanelContent == null || teacherScheduleOpen) return;
        teachersPanelContent.removeAllViews();

        String q = query == null ? "" : query.trim().toLowerCase(ru);

        if (teacherItems.isEmpty()) {
            addTeacherMessage("Список пока пуст", "Подожди пару секунд. Если список не появится, проверь интернет.");
            return;
        }

        int shown = 0;
        for (TeacherItem teacher : teacherItems) {
            if (!q.isEmpty() && !teacher.name.toLowerCase(ru).contains(q)) continue;
            addTeacherCard(teacher);
            shown++;
            if (shown >= 80) break;
        }

        if (shown == 0) {
            addTeacherMessage("Ничего не найдено", "Попробуй ввести фамилию короче или проверь раскладку.");
        }
    }

    private void addTeacherCard(TeacherItem teacher) {
        LinearLayout card = settingsCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(teacher.name);
        name.setTextColor(textMain);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(greenDark);
        arrow.setTextSize(24);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(32), dp(44)));

        card.setOnClickListener(v -> openTeacherSchedule(teacher));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        teachersPanelContent.addView(card, params);
    }

    private void addTeacherMessage(String title, String message) {
        if (teachersPanelContent == null) return;
        LinearLayout card = settingsCard();
        card.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(textMain);
        titleView.setTextSize(17);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(titleView);

        TextView messageView = new TextView(this);
        messageView.setText(message);
        messageView.setTextColor(textMuted);
        messageView.setTextSize(14);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.setMargins(0, dp(8), 0, 0);
        card.addView(messageView, msgParams);

        teachersPanelContent.addView(card);
    }

    private void openTeacherSchedule(TeacherItem teacher) {
        teacherScheduleOpen = true;
        refreshTeachersPanelContent("");
        addTeacherMessage("Загружаю расписание", teacher.name);

        new Thread(() -> {
            try {
                String html = fetchUrl(TEACHERS_URL + "index.php?teacher=" + teacher.id);
                String cleaned = cleanupSection(stripHtml(html));
                main.post(() -> showTeacherScheduleText(teacher, cleaned));
            } catch (Exception e) {
                main.post(() -> {
                    if (teachersPanelContent != null) {
                        teachersPanelContent.removeAllViews();
                        addTeacherMessage("Не удалось загрузить расписание", e.getMessage() == null ? "Ошибка сети." : e.getMessage());
                    }
                });
            }
        }).start();
    }

    private void showTeacherScheduleText(TeacherItem teacher, String scheduleText) {
        if (teachersPanelContent == null) return;
        teachersPanelContent.removeAllViews();

        TextView teacherName = new TextView(this);
        teacherName.setText(teacher.name);
        teacherName.setTextColor(textMain);
        teacherName.setTextSize(21);
        teacherName.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.setMargins(0, 0, 0, dp(12));
        teachersPanelContent.addView(teacherName, nameParams);

        LinearLayout card = settingsCard();
        card.setOrientation(LinearLayout.VERTICAL);

        TextView body = new TextView(this);
        body.setText(scheduleText == null || scheduleText.trim().isEmpty()
                ? "Расписание не найдено."
                : scheduleText);
        body.setTextColor(textMain);
        body.setTextSize(14);
        body.setLineSpacing(dp(2), 1.0f);
        card.addView(body);

        teachersPanelContent.addView(card);
    }

    private void showEasterEgg() {
        if (easterEggPanel == null) {
            buildEasterEggPanel();
        }
        easterEggOpen = true;
        easterEggPanel.setVisibility(View.VISIBLE);
        easterEggPanel.setAlpha(0f);
        easterEggPanel.bringToFront();
        easterEggPanel.animate().alpha(1f).setDuration(180).start();
    }

    private void hideEasterEgg() {
        if (easterEggPanel == null) return;
        easterEggOpen = false;
        easterEggPanel.animate()
                .alpha(0f)
                .setDuration(160)
                .withEndAction(() -> easterEggPanel.setVisibility(View.GONE))
                .start();
    }

    private void buildEasterEggPanel() {
        easterEggPanel = new LinearLayout(this);
        easterEggPanel.setOrientation(LinearLayout.VERTICAL);
        easterEggPanel.setGravity(Gravity.CENTER);
        easterEggPanel.setPadding(dp(18), dp(18), dp(18), dp(18));
        easterEggPanel.setBackgroundColor(Color.rgb(10, 10, 10));
        easterEggPanel.setVisibility(View.GONE);
        easterEggPanel.setClickable(true);
        easterEggPanel.setFocusable(true);

        ImageView image = new ImageView(this);
        image.setImageResource(getResources().getIdentifier("easter_egg", "drawable", getPackageName()));
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        easterEggPanel.addView(image, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        TextView hint = new TextView(this);
        hint.setText("Назад — выйти из пасхалки");
        hint.setTextColor(Color.argb(185, 255, 255, 255));
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        easterEggPanel.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        appFrame.addView(easterEggPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
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
        ScheduleWidgetProvider.updateAllWidgets(this);
    }

    private void updateSubgroupUi() {
        if (groupPill != null) {
            groupPill.setText("Группа " + scheduleGroup);
        }
        if (subgroupPill != null) {
            subgroupPill.setText(subgroupDisplayShort());
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
        if (refreshButton != null) {
            refreshButton.setText(showWholeWeek ? "Проверить изменения" : "Обновить");
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
        if (themeMode == null || themeMode.trim().isEmpty()) {
            themeMode = darkMode ? "green_dark" : "green_light";
        }
        darkMode = themeMode.endsWith("_dark");

        switch (themeMode) {
            case "green_dark":
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
                break;

            case "blue_light":
                greenDark = Color.rgb(24, 78, 125);
                green = Color.rgb(52, 116, 178);
                greenSoft = Color.rgb(224, 239, 252);
                background = Color.rgb(247, 250, 253);
                cardSurface = Color.rgb(255, 255, 255);
                textMain = Color.rgb(25, 31, 38);
                textMuted = Color.rgb(83, 96, 110);
                border = Color.rgb(213, 226, 238);
                headerStart = Color.rgb(28, 84, 138);
                headerEnd = Color.rgb(64, 138, 204);
                roomChipBg = Color.rgb(235, 245, 253);
                roomChipStroke = Color.rgb(166, 200, 229);
                buildingChipBg = Color.rgb(222, 238, 251);
                buildingChipStroke = Color.rgb(136, 181, 221);
                buildingChipText = Color.rgb(24, 78, 125);
                break;

            case "blue_dark":
                greenDark = Color.rgb(190, 222, 255);
                green = Color.rgb(80, 152, 226);
                greenSoft = Color.rgb(24, 43, 63);
                background = Color.rgb(14, 19, 27);
                cardSurface = Color.rgb(25, 32, 42);
                textMain = Color.rgb(234, 240, 247);
                textMuted = Color.rgb(178, 191, 205);
                border = Color.rgb(55, 70, 88);
                headerStart = Color.rgb(19, 50, 86);
                headerEnd = Color.rgb(36, 91, 148);
                roomChipBg = Color.rgb(29, 42, 57);
                roomChipStroke = Color.rgb(70, 106, 145);
                buildingChipBg = Color.rgb(24, 51, 78);
                buildingChipStroke = Color.rgb(78, 128, 180);
                buildingChipText = Color.rgb(220, 239, 255);
                break;

            case "purple_light":
                greenDark = Color.rgb(89, 52, 138);
                green = Color.rgb(126, 87, 194);
                greenSoft = Color.rgb(241, 232, 255);
                background = Color.rgb(251, 248, 255);
                cardSurface = Color.rgb(255, 255, 255);
                textMain = Color.rgb(32, 28, 38);
                textMuted = Color.rgb(96, 86, 110);
                border = Color.rgb(228, 218, 239);
                headerStart = Color.rgb(86, 50, 132);
                headerEnd = Color.rgb(137, 99, 201);
                roomChipBg = Color.rgb(248, 242, 255);
                roomChipStroke = Color.rgb(205, 183, 235);
                buildingChipBg = Color.rgb(240, 230, 252);
                buildingChipStroke = Color.rgb(180, 151, 222);
                buildingChipText = Color.rgb(89, 52, 138);
                break;

            case "purple_dark":
                greenDark = Color.rgb(226, 205, 255);
                green = Color.rgb(166, 122, 232);
                greenSoft = Color.rgb(45, 34, 63);
                background = Color.rgb(20, 17, 26);
                cardSurface = Color.rgb(31, 26, 39);
                textMain = Color.rgb(242, 238, 247);
                textMuted = Color.rgb(198, 187, 211);
                border = Color.rgb(72, 59, 88);
                headerStart = Color.rgb(57, 34, 89);
                headerEnd = Color.rgb(93, 61, 140);
                roomChipBg = Color.rgb(39, 32, 50);
                roomChipStroke = Color.rgb(103, 81, 139);
                buildingChipBg = Color.rgb(50, 36, 72);
                buildingChipStroke = Color.rgb(128, 93, 178);
                buildingChipText = Color.rgb(242, 231, 255);
                break;

            case "amber_light":
                greenDark = Color.rgb(117, 74, 10);
                green = Color.rgb(184, 120, 25);
                greenSoft = Color.rgb(252, 239, 213);
                background = Color.rgb(255, 251, 242);
                cardSurface = Color.rgb(255, 255, 250);
                textMain = Color.rgb(40, 31, 22);
                textMuted = Color.rgb(105, 91, 72);
                border = Color.rgb(235, 219, 190);
                headerStart = Color.rgb(133, 82, 16);
                headerEnd = Color.rgb(196, 132, 36);
                roomChipBg = Color.rgb(255, 246, 229);
                roomChipStroke = Color.rgb(226, 195, 140);
                buildingChipBg = Color.rgb(252, 237, 204);
                buildingChipStroke = Color.rgb(211, 169, 94);
                buildingChipText = Color.rgb(117, 74, 10);
                break;

            case "amber_dark":
                greenDark = Color.rgb(255, 220, 150);
                green = Color.rgb(221, 151, 45);
                greenSoft = Color.rgb(58, 43, 24);
                background = Color.rgb(23, 19, 14);
                cardSurface = Color.rgb(35, 29, 22);
                textMain = Color.rgb(248, 241, 230);
                textMuted = Color.rgb(207, 193, 171);
                border = Color.rgb(78, 63, 43);
                headerStart = Color.rgb(78, 49, 13);
                headerEnd = Color.rgb(132, 83, 22);
                roomChipBg = Color.rgb(42, 34, 24);
                roomChipStroke = Color.rgb(128, 95, 47);
                buildingChipBg = Color.rgb(61, 43, 22);
                buildingChipStroke = Color.rgb(160, 112, 44);
                buildingChipText = Color.rgb(255, 236, 190);
                break;

            default:
                themeMode = "green_light";
                darkMode = false;
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
                break;
        }
    }

    private void toggleTheme() {
        String base = themeMode == null ? "green" : themeMode.replace("_dark", "").replace("_light", "");
        setThemeMode(base + (darkMode ? "_light" : "_dark"));
    }

    private void applyThemeToViews() {
        applyPalette();
        getWindow().setStatusBarColor(headerStart);
        getWindow().setNavigationBarColor(background);
        if (appFrame != null) appFrame.setBackgroundColor(background);
        if (rootLayout != null) rootLayout.setBackgroundColor(background);
        if (headerLayout != null) headerLayout.setBackground(headerBackground());
        if (settingsPanel != null) settingsPanel.setBackgroundColor(background);
        if (themePanel != null) themePanel.setBackgroundColor(background);
        if (teachersPanel != null) teachersPanel.setBackgroundColor(background);
        if (groupPill != null) groupPill.setBackground(pillBackground(Color.argb(48, 255, 255, 255), Color.argb(70, 255, 255, 255)));
        if (subgroupPill != null) subgroupPill.setBackground(pillBackground(Color.argb(48, 255, 255, 255), Color.argb(70, 255, 255, 255)));
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
        if (settingsOpen) refreshSettingsPanelContent();
        if (themePanelOpen) refreshThemePanelContent();
        if (teachersPanelOpen && !teacherScheduleOpen) refreshTeachersPanelContent("");
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
            days.add(new DaySchedule(displayDate, "Сегодня", clean, pairs, true));
        }

        DaySchedule today = findTodaySchedule(days);
        List<String> todayPairs = today == null ? new ArrayList<>() : today.pairs;
        return new ScheduleResult(today == null ? displayDate : today.displayDate, clean, todayPairs, days);
    }

    private List<DaySchedule> splitDays(String fullWeekText) {
        List<DaySchedule> result = new ArrayList<>();

        String prepared = fullWeekText.replaceAll(
                "(?i)(?<!\\n)(\\d{2}\\.\\d{2}\\.\\d{4}\\s+(?:Понедельник|Вторник|Среда|Четверг|Пятница|Суббота|Воскресенье))",
                "\n$1"
        );

        Pattern dayPattern = Pattern.compile(
                "(?i)(\\d{2}\\.\\d{2}\\.\\d{4})\\s+" +
                        "(Понедельник|Вторник|Среда|Четверг|Пятница|Суббота|Воскресенье)" +
                        "(\\s*\\(сегодня\\))?"
        );
        Matcher matcher = dayPattern.matcher(prepared);

        List<Integer> starts = new ArrayList<>();
        List<String> dates = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Boolean> todayMarks = new ArrayList<>();

        while (matcher.find()) {
            starts.add(matcher.start());
            dates.add(matcher.group(1));
            names.add(matcher.group(2));
            todayMarks.add(matcher.group(3) != null);
        }

        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : prepared.length();
            String block = prepared.substring(start, end).trim();
            List<String> pairs = splitPairs(block);
            boolean isActualToday = dates.get(i).equals(todayString());
            result.add(new DaySchedule(dates.get(i), names.get(i), block, pairs, isActualToday));
        }

        return result;
    }

    private DaySchedule findTodaySchedule(List<DaySchedule> days) {
        String today = todayString();
        for (DaySchedule day : days) {
            if (today.equals(day.displayDate)) return day;
        }

        for (DaySchedule day : days) {
            if (day.isToday && today.equals(day.displayDate)) return day;
        }

        return null;
    }

    private String cleanupSection(String section) {
        String text = section.replace('\u00A0', ' ')
                .replace('–', '-')
                .replace('—', '-')
                .replace("\r", "\n");
        text = text.replaceAll("(?i)(?<!\\n)(\\d{2}\\.\\d{2}\\.\\d{4}\\s+(?:Понедельник|Вторник|Среда|Четверг|Пятница|Суббота|Воскресенье))", "\n$1");
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
        dateView.setText(today == null ? "Сегодня • " + todayString() : "Сегодня • " + today.displayDate);
        List<String> visiblePairs = today == null ? new ArrayList<>() : filterPairsForSubgroup(today.pairs);
        summaryView.setText(summaryText(visiblePairs.size(), "Сегодня") + " • " + subgroupDisplay() + " • режим: сегодня");

        if (visiblePairs.isEmpty()) {
            addMessageCard("Пар не найдено", "Для выбранной подгруппы на сегодня пары не найдены. Проверь режим «Неделя» или переключись на «Все».");
        } else {
            for (String pair : visiblePairs) {
                addCompactPairCard(listLayout, pair);
            }
        }
    }

    private void showWholeWeekSchedule(ScheduleResult result) {
        titleView.setText("Неделя");
        dateView.setText("Неделя разделена по дням");
        int total = 0;
        for (DaySchedule day : result.days) {
            total += filterPairsForSubgroup(day.pairs).size();
        }
        summaryView.setText(weekSummaryText(total) + " • " + result.days.size() + " дней • " + subgroupDisplay());

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
            for (String pair : pairs) {
                visible.addAll(splitPairForAllSubgroups(pair));
            }
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

    private List<String> splitPairForAllSubgroups(String pair) {
        List<String> result = new ArrayList<>();
        String first = filterPairForSubgroup(pair, SUBGROUP_1);
        String second = filterPairForSubgroup(pair, SUBGROUP_2);

        if (first.isEmpty() && second.isEmpty()) {
            if (!pair.trim().isEmpty()) result.add(pair.trim());
            return result;
        }

        if (!first.isEmpty() && first.equals(second)) {
            result.add(first);
            return result;
        }

        if (!first.isEmpty()) result.add(first);
        if (!second.isEmpty()) result.add(second);
        return result;
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
        return "На неделе " + pairCountText(pairCount);
    }

    private String pairCountText(int pairCount) {
        if (pairCount <= 0) return "0 пар";
        if (pairCount % 10 == 1 && pairCount % 100 != 11) return pairCount + " пара";
        if (pairCount % 10 >= 2 && pairCount % 10 <= 4 && (pairCount % 100 < 10 || pairCount % 100 >= 20)) {
            return pairCount + " пары";
        }
        return pairCount + " пар";
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
        ScheduleWidgetProvider.updateAllWidgets(this);
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
    dayCard.setPadding(dp(10), dp(10), dp(10), dp(8));
    dayCard.setBackground(cardBackground(day.isToday ? (darkMode ? greenSoft : greenSoft) : cardSurface));
    dayCard.setElevation(dp(1));

    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(10), dp(6), dp(10), dp(6));
    header.setBackground(pillBackground(day.isToday ? greenSoft : (darkMode ? cardSurface : cardSurface), day.isToday ? green : border));
    dayCard.addView(header);

    LinearLayout dayTextBox = new LinearLayout(this);
    dayTextBox.setOrientation(LinearLayout.VERTICAL);
    header.addView(dayTextBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

    TextView dayTitle = new TextView(this);
    String label = day.dayName;
    if (day.isToday) label += " • сегодня";
    dayTitle.setText(label);
    dayTitle.setTextColor(day.isToday ? greenDark : textMain);
    dayTitle.setTextSize(16);
    dayTitle.setTypeface(Typeface.DEFAULT_BOLD);
    dayTextBox.addView(dayTitle);

    TextView dayDate = new TextView(this);
    dayDate.setText(day.displayDate);
    dayDate.setTextColor(textMuted);
    dayDate.setTextSize(12);
    dayTextBox.addView(dayDate);

    TextView countView = new TextView(this);
    countView.setText(pairCountText(pairs.size()));
    countView.setTextColor(day.isToday ? Color.WHITE : greenDark);
    countView.setTextSize(13);
    countView.setTypeface(Typeface.DEFAULT_BOLD);
    countView.setGravity(Gravity.CENTER);
    countView.setPadding(dp(10), dp(5), dp(10), dp(5));
    countView.setBackground(pillBackground(day.isToday ? green : greenSoft, day.isToday ? green : border));
    header.addView(countView);

    for (String pair : pairs) {
        addWeekPairRow(dayCard, pair, day.isToday);
    }

    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.setMargins(0, 0, 0, dp(10));
    listLayout.addView(dayCard, params);
}

private void addWeekPairRow(LinearLayout parent, String pairText, boolean isToday) {
    ParsedPair parsed = parsePairCard(pairText);
    boolean currentPair = isToday && isCurrentPair(parsed);

    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setPadding(dp(10), dp(8), dp(10), dp(8));
    row.setBackground(locationBackground(
            currentPair ? (darkMode ? greenSoft : greenSoft) : (darkMode ? cardSurface : cardSurface),
            currentPair ? green : border));

    LinearLayout top = new LinearLayout(this);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    row.addView(top);

    TextView pairChip = new TextView(this);
    pairChip.setText(currentPair ? parsed.pairName.replace(" пара", "") + " • сейчас" : parsed.pairName.replace(" пара", ""));
    pairChip.setTextColor(currentPair ? Color.WHITE : greenDark);
    pairChip.setTextSize(12);
    pairChip.setTypeface(Typeface.DEFAULT_BOLD);
    pairChip.setGravity(Gravity.CENTER);
    pairChip.setPadding(dp(8), dp(3), dp(8), dp(3));
    pairChip.setBackground(pillBackground(currentPair ? green : greenSoft, currentPair ? green : border));
    top.addView(pairChip);

    TextView lessonView = new TextView(this);
    String lesson = extractLessonTitle(parsed);
    lessonView.setText(lesson.isEmpty() ? "Пара" : lesson);
    lessonView.setTextColor(textMain);
    lessonView.setTextSize(14);
    lessonView.setTypeface(Typeface.DEFAULT_BOLD);
    lessonView.setSingleLine(false);
    LinearLayout.LayoutParams lessonParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    lessonParams.setMargins(dp(8), 0, dp(8), 0);
    top.addView(lessonView, lessonParams);

    TextView timeView = new TextView(this);
    timeView.setText(parsed.time);
    timeView.setTextColor(textMain);
    timeView.setTextSize(13);
    timeView.setTypeface(Typeface.DEFAULT_BOLD);
    timeView.setGravity(Gravity.RIGHT);
    top.addView(timeView);

    LinearLayout bottom = new LinearLayout(this);
    bottom.setOrientation(LinearLayout.HORIZONTAL);
    bottom.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    bottomParams.setMargins(0, dp(6), 0, 0);
    row.addView(bottom, bottomParams);

    TextView teacherView = new TextView(this);
    String teacher = extractTeacherTitle(parsed);
    teacherView.setText(teacher.isEmpty() ? "Преподаватель не указан" : teacher);
    teacherView.setTextColor(textMuted);
    teacherView.setTextSize(12);
    teacherView.setSingleLine(true);
    bottom.addView(teacherView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

    TextView placeView = new TextView(this);
    placeView.setText(shortPlaceTitle(parsed));
    placeView.setTextColor(greenDark);
    placeView.setTextSize(12);
    placeView.setTypeface(Typeface.DEFAULT_BOLD);
    placeView.setGravity(Gravity.RIGHT);
    LinearLayout.LayoutParams placeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    placeParams.setMargins(dp(8), 0, 0, 0);
    bottom.addView(placeView, placeParams);

    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    rowParams.setMargins(0, dp(7), 0, 0);
    parent.addView(row, rowParams);
}

private boolean isCurrentPair(ParsedPair parsed) {
    if (parsed == null || parsed.time == null) return false;

    Matcher matcher = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})").matcher(parsed.time);
    if (!matcher.find()) return false;

    try {
        int start = Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2));
        int end = Integer.parseInt(matcher.group(3)) * 60 + Integer.parseInt(matcher.group(4));

        Calendar now = Calendar.getInstance();
        int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        if (end < start) end += 24 * 60;
        if (current < start && end >= 24 * 60) current += 24 * 60;

        return current >= start && current <= end;
    } catch (Exception ignored) {
        return false;
    }
}

private String extractLessonTitle(ParsedPair parsed) {
    for (String line : parsed.lines) {
        String pretty = prettifyLessonLine(line);
        if (!pretty.startsWith("Аудитория:") && !pretty.startsWith("Преподаватель:") && isLessonLine(pretty)) {
            return pretty;
        }
    }
    return "";
}

private String extractTeacherTitle(ParsedPair parsed) {
    for (String line : parsed.lines) {
        String pretty = prettifyLessonLine(line);
        if (pretty.startsWith("Преподаватель:")) {
            return pretty.replace("Преподаватель:", "").trim();
        }
    }
    return "";
}

private String shortPlaceTitle(ParsedPair parsed) {
    for (String line : parsed.lines) {
        String pretty = prettifyLessonLine(line);
        if (pretty.startsWith("Аудитория:")) {
            String place = pretty.replaceFirst("(?i)^Аудитория:\\s*", "").trim();
            if (isHorkinaPlace(place)) return "Хоркина";
            Matcher matcher = Pattern.compile("(?i)^(.*?)(?:,\\s*)?(учебный\\s+корпус\\s*№?\\s*\\d+.*)$").matcher(place);
            if (matcher.find()) {
                String room = matcher.group(1).replaceAll(",\\s*$", "").trim();
                String building = formatBuildingValue(matcher.group(2).trim());
                if (!room.isEmpty()) return room + " • " + building;
                return building;
            }
            return place;
        }
    }
    return "";
}

private void addCompactPairCard(LinearLayout parent, String pairText) {
    ParsedPair parsed = parsePairCard(pairText);
    boolean currentPair = isCurrentPair(parsed);

    LinearLayout item = new LinearLayout(this);
    item.setOrientation(LinearLayout.VERTICAL);
    item.setPadding(dp(11), dp(9), dp(11), dp(9));
    item.setBackground(currentPair
            ? locationBackground(darkMode ? greenSoft : greenSoft, green)
            : cardBackground(darkMode ? cardSurface : cardSurface));

    LinearLayout head = new LinearLayout(this);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(Gravity.CENTER_VERTICAL);
    item.addView(head);

    TextView pairChip = new TextView(this);
    pairChip.setText(currentPair ? parsed.pairName + " • сейчас" : parsed.pairName);
    pairChip.setTextColor(currentPair ? Color.WHITE : greenDark);
    pairChip.setTextSize(13);
    pairChip.setTypeface(Typeface.DEFAULT_BOLD);
    pairChip.setPadding(dp(9), dp(3), dp(9), dp(3));
    pairChip.setBackground(pillBackground(currentPair ? green : greenSoft, currentPair ? green : border));
    head.addView(pairChip);

    TextView timeView = new TextView(this);
    timeView.setText(parsed.time);
    timeView.setTextColor(textMain);
    timeView.setTextSize(15);
    timeView.setTypeface(Typeface.DEFAULT_BOLD);
    timeView.setGravity(Gravity.RIGHT);
    LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    timeParams.setMargins(dp(10), 0, 0, 0);
    head.addView(timeView, timeParams);

    String lessonLine = "";
    String teacherLine = "";
    String locationLine = "";

    for (String line : parsed.lines) {
        String pretty = prettifyLessonLine(line);
        if (pretty.startsWith("Аудитория:")) {
            locationLine = pretty;
        } else if (pretty.startsWith("Преподаватель:")) {
            teacherLine = pretty;
        } else if (lessonLine.isEmpty() && isLessonLine(pretty)) {
            lessonLine = pretty;
        }
    }

    if (!lessonLine.isEmpty()) {
        TextView lessonView = new TextView(this);
        lessonView.setText(lessonLine);
        lessonView.setTextColor(textMain);
        lessonView.setTextSize(15);
        lessonView.setTypeface(Typeface.DEFAULT_BOLD);
        lessonView.setLineSpacing(dp(1), 1.0f);
        LinearLayout.LayoutParams lessonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lessonParams.setMargins(0, dp(8), 0, 0);
        item.addView(lessonView, lessonParams);
    }

    if (!teacherLine.isEmpty()) {
        TextView teacherView = new TextView(this);
        teacherView.setText(teacherLine);
        teacherView.setTextColor(textMuted);
        teacherView.setTextSize(12);
        teacherView.setLineSpacing(dp(1), 1.0f);
        LinearLayout.LayoutParams teacherParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        teacherParams.setMargins(0, dp(6), 0, 0);
        item.addView(teacherView, teacherParams);
    }

    if (!locationLine.isEmpty()) {
        LinearLayout locationWrap = new LinearLayout(this);
        locationWrap.setOrientation(LinearLayout.HORIZONTAL);
        locationWrap.setWeightSum(2f);
        LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapParams.setMargins(0, dp(8), 0, 0);
        item.addView(locationWrap, wrapParams);

        String place = locationLine.replaceFirst("(?i)^Аудитория:\\s*", "").trim();
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
        if (isHorkinaPlace(place)) {
            building = "Хоркина";
        }

        if (!room.isEmpty()) {
            LinearLayout roomCard = compactInfoChip("Аудитория", room, roomChipBg, roomChipStroke, greenDark);
            locationWrap.addView(roomCard, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }

        if (!building.isEmpty()) {
            LinearLayout buildingCard = compactInfoChip("Корпус", formatBuildingValue(building), buildingChipBg, buildingChipStroke, buildingChipText);
            LinearLayout.LayoutParams buildingParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            if (!room.isEmpty()) buildingParams.setMargins(dp(8), 0, 0, 0);
            locationWrap.addView(buildingCard, buildingParams);
        }
    }

    LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    itemParams.setMargins(0, 0, 0, dp(6));
    parent.addView(item, itemParams);
}

private LinearLayout compactInfoChip(String label, String value, int bg, int stroke, int valueColor) {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(10), dp(8), dp(10), dp(8));
    card.setBackground(locationBackground(bg, stroke));

    TextView labelView = new TextView(this);
    labelView.setText(label);
    labelView.setTextColor(textMuted);
    labelView.setTextSize(11);
    labelView.setTypeface(Typeface.DEFAULT_BOLD);
    card.addView(labelView);

    TextView valueView = new TextView(this);
    valueView.setText(value);
    valueView.setTextColor(valueColor);
    valueView.setTextSize(15);
    valueView.setTypeface(Typeface.DEFAULT_BOLD);
    LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    valueParams.setMargins(0, dp(2), 0, 0);
    card.addView(valueView, valueParams);

    return card;
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
        if (isHorkinaPlace(place)) {
            building = "Хоркина";
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

    private boolean isHorkinaPlace(String place) {
        String lower = place.toLowerCase(ru);
        return lower.contains("универсальный") && lower.contains("спортив")
                || lower.contains("уск")
                || lower.contains("хоркин");
    }

    private String formatBuildingValue(String building) {
        if (isHorkinaPlace(building)) return "Хоркина";
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
                .setNegativeButton("Отмена", null);

        if (!updateInfo.apkUrl.isEmpty()) {
            dialog.setPositiveButton("Скачать", (d, which) -> downloadUpdateApk(updateInfo.apkUrl, updateInfo.tagName));
        } else {
            dialog.setMessage(message + "\n\nAPK-файл не прикреплён к релизу. Прикрепи APK в GitHub Releases.");
            dialog.setPositiveButton("ОК", null);
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

        if (tryOpenExistingDownloadedApk(tagName, apkUrl)) {
            return;
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("Расписание БелГУ " + tagName);
            request.setDescription("Скачиваю обновление APK");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // Не указываем свою папку загрузки: на некоторых прошивках Android/data
            // недоступна для создания через DownloadManager. Системный DownloadManager
            // сам выберет безопасное место и отдаст URI скачанного APK.
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) throw new Exception("DownloadManager недоступен");
            updateDownloadId = manager.enqueue(request);
            saveUpdateDownload(updateDownloadId, tagName, apkUrl);
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
                    Uri apkUri = getDownloadedApkUri(manager, cursor, id);

                    if (apkUri != null) {
                        Toast.makeText(this, "Обновление скачано. Открываю установку...", Toast.LENGTH_LONG).show();
                        Uri finalApkUri = apkUri;
                        main.postDelayed(() -> openApkInstaller(finalApkUri), 350);
                    } else {
                        showDownloadOpenError("APK скачан, но система не отдала ссылку на файл. Открой APK из уведомления загрузки.");
                    }
                } else {
                    showDownloadOpenError("Обновление не скачалось. Проверь интернет и попробуй ещё раз.");
                }
            } finally {
                cursor.close();
            }
        } catch (Exception e) {
            showDownloadOpenError("Не удалось открыть установщик APK: " + (e.getMessage() == null ? "неизвестная ошибка" : e.getMessage()));
        }
    }

    private boolean tryOpenExistingDownloadedApk(String tagName, String apkUrl) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        long savedId = prefs.getLong(KEY_UPDATE_DOWNLOAD_ID, -1L);
        String savedTag = prefs.getString(KEY_UPDATE_DOWNLOAD_TAG, "");
        String savedUrl = prefs.getString(KEY_UPDATE_DOWNLOAD_URL, "");

        if (savedId <= 0 || !tagName.equals(savedTag) || !apkUrl.equals(savedUrl)) {
            return false;
        }

        try {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) return false;

            DownloadManager.Query query = new DownloadManager.Query().setFilterById(savedId);
            Cursor cursor = manager.query(query);
            if (cursor == null) return false;

            try {
                if (!cursor.moveToFirst()) {
                    clearSavedUpdateDownload();
                    return false;
                }

                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(statusIndex);

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Uri apkUri = getDownloadedApkUri(manager, cursor, savedId);
                    if (apkUri != null) {
                        updateDownloadId = savedId;
                        Toast.makeText(this, "APK уже скачан. Открываю установку...", Toast.LENGTH_LONG).show();
                        main.postDelayed(() -> openApkInstaller(apkUri), 250);
                        return true;
                    }
                    clearSavedUpdateDownload();
                    return false;
                }

                if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED) {
                    updateDownloadId = savedId;
                    Toast.makeText(this, "APK уже скачивается. Проверь уведомление загрузки.", Toast.LENGTH_LONG).show();
                    return true;
                }

                clearSavedUpdateDownload();
                return false;
            } finally {
                cursor.close();
            }
        } catch (Exception ignored) {
            clearSavedUpdateDownload();
            return false;
        }
    }

    private Uri getDownloadedApkUri(DownloadManager manager, Cursor cursor, long id) {
        Uri apkUri = manager.getUriForDownloadedFile(id);

        if (apkUri == null && cursor != null) {
            int localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            if (localUriIndex >= 0) {
                String localUri = cursor.getString(localUriIndex);
                if (localUri != null && !localUri.trim().isEmpty()) {
                    apkUri = Uri.parse(localUri);
                }
            }
        }

        return apkUri;
    }

    private void saveUpdateDownload(long id, String tagName, String apkUrl) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putLong(KEY_UPDATE_DOWNLOAD_ID, id)
                .putString(KEY_UPDATE_DOWNLOAD_TAG, tagName)
                .putString(KEY_UPDATE_DOWNLOAD_URL, apkUrl)
                .apply();
    }

    private void clearSavedUpdateDownload() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .remove(KEY_UPDATE_DOWNLOAD_ID)
                .remove(KEY_UPDATE_DOWNLOAD_TAG)
                .remove(KEY_UPDATE_DOWNLOAD_URL)
                .apply();
        updateDownloadId = -1L;
    }

    private void openApkInstaller(Uri apkUri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivity(intent);
        } catch (Exception e) {
            showDownloadOpenError("Не удалось автоматически открыть установщик. Открой скачанный APK из уведомления загрузки.");
        }
    }

    private void showDownloadOpenError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Установка APK")
                .setMessage(message)
                .setPositiveButton("ОК", null)
                .show();
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
        final boolean isToday;

        DaySchedule(String displayDate, String dayName, String fullText, List<String> pairs, boolean isToday) {
            this.displayDate = displayDate;
            this.dayName = dayName;
            this.fullText = fullText;
            this.pairs = pairs;
            this.isToday = isToday;
        }
    }

    private static class TeacherItem {
        final String id;
        final String name;

        TeacherItem(String id, String name) {
            this.id = id;
            this.name = name;
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
