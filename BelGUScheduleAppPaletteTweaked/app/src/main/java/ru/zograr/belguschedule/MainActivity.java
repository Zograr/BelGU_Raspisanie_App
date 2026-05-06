package ru.zograr.belguschedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private static final String DEFAULT_GROUP = "90002595";
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
    private TextView themeToggleButton;
    private WebView hiddenWebView;

    private String scheduleUrl;
    private String scheduleGroup = DEFAULT_GROUP;
    private String selectedSubgroup = SUBGROUP_ALL;
    private boolean darkMode = false;
    private ScheduleResult currentResult;
    private boolean mainFrameError = false;

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
        darkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        applyPalette();
        buildUi();
        setupWebView();
        loadCachedSchedule();
        refreshSchedule();
    }

    @Override
    protected void onDestroy() {
        if (hiddenWebView != null) {
            hiddenWebView.stopLoading();
            hiddenWebView.destroy();
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

        themeToggleButton = new TextView(this);
        themeToggleButton.setGravity(Gravity.CENTER);
        themeToggleButton.setTextSize(18);
        themeToggleButton.setTypeface(Typeface.DEFAULT_BOLD);
        themeToggleButton.setTextColor(Color.WHITE);
        themeToggleButton.setBackground(pillBackground(Color.argb(52, 255, 255, 255), Color.argb(90, 255, 255, 255)));
        themeToggleButton.setOnClickListener(v -> toggleTheme());
        LinearLayout.LayoutParams themeParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        themeParams.setMargins(dp(8), 0, dp(8), 0);
        headerTop.addView(themeToggleButton, themeParams);

        subgroupMenuButton = new Button(this);
        subgroupMenuButton.setAllCaps(false);
        subgroupMenuButton.setTextSize(13);
        subgroupMenuButton.setTypeface(Typeface.DEFAULT_BOLD);
        subgroupMenuButton.setTextColor(Color.WHITE);
        subgroupMenuButton.setMinHeight(0);
        subgroupMenuButton.setMinimumHeight(0);
        subgroupMenuButton.setPadding(dp(12), 0, dp(12), 0);
        subgroupMenuButton.setBackground(pillBackground(Color.argb(52, 255, 255, 255), Color.argb(90, 255, 255, 255)));
        subgroupMenuButton.setOnClickListener(v -> showSubgroupMenu());
        headerTop.addView(subgroupMenuButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)));

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
        return key + "_" + scheduleGroup;
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

    private void showSubgroupMenu() {
        PopupMenu menu = new PopupMenu(this, subgroupMenuButton);
        menu.getMenu().add(0, 1, 0, "1 подгруппа");
        menu.getMenu().add(0, 2, 1, "2 подгруппа");
        menu.getMenu().add(0, 3, 2, "Все подгруппы");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) selectSubgroup(SUBGROUP_1);
            if (item.getItemId() == 2) selectSubgroup(SUBGROUP_2);
            if (item.getItemId() == 3) selectSubgroup(SUBGROUP_ALL);
            return true;
        });
        menu.show();
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
            groupPill.setText("Группа " + scheduleGroup + "  ✎");
        }
        if (subgroupMenuButton != null) {
            subgroupMenuButton.setText(subgroupDisplayShort() + "  ▾");
        }
        if (themeToggleButton != null) {
            themeToggleButton.setText(darkMode ? "☾" : "☀");
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
            greenDark = Color.rgb(116, 232, 150);
            green = Color.rgb(42, 184, 90);
            greenSoft = Color.rgb(24, 54, 34);
            background = Color.rgb(10, 14, 12);
            cardSurface = Color.rgb(20, 26, 23);
            textMain = Color.rgb(245, 250, 246);
            textMuted = Color.rgb(181, 193, 185);
            border = Color.rgb(49, 67, 55);
            headerStart = Color.rgb(3, 43, 15);
            headerEnd = Color.rgb(6, 84, 33);
            roomChipBg = Color.rgb(22, 38, 28);
            roomChipStroke = Color.rgb(61, 136, 84);
            buildingChipBg = Color.rgb(16, 47, 26);
            buildingChipStroke = Color.rgb(48, 146, 79);
            buildingChipText = Color.rgb(228, 255, 234);
        } else {
            greenDark = Color.rgb(8, 73, 20);
            green = Color.rgb(31, 121, 50);
            greenSoft = Color.rgb(231, 245, 234);
            background = Color.rgb(244, 247, 244);
            cardSurface = Color.WHITE;
            textMain = Color.rgb(28, 32, 29);
            textMuted = Color.rgb(95, 105, 98);
            border = Color.rgb(223, 231, 224);
            headerStart = Color.rgb(6, 70, 18);
            headerEnd = Color.rgb(24, 117, 42);
            roomChipBg = Color.rgb(241, 249, 243);
            roomChipStroke = Color.rgb(167, 211, 179);
            buildingChipBg = Color.rgb(232, 245, 236);
            buildingChipStroke = Color.rgb(121, 185, 140);
            buildingChipText = Color.rgb(18, 88, 39);
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
        statusView.setText("Загружаю расписание...");
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
            String text = object.optString("text", "").trim();
            boolean ok = object.optBoolean("ok", false);

            if (ok && looksLikeSchedule(text)) {
                ScheduleResult result = parseSchedule(date, text);
                saveCache(result.displayDate, result.fullText);
                showSchedule(result, false);
                return;
            }

            if (attempt < MAX_EXTRACT_ATTEMPTS && !mainFrameError) {
                statusView.setText("Жду, пока сайт догрузит расписание... попытка " + (attempt + 1));
                attemptExtract(attempt + 1);
                return;
            }

            String debug = object.optString("debug", "");
            if (!debug.isEmpty()) {
                showLoadFailure("Не нашёл блок расписания на сегодня. Сайт мог изменить разметку.");
            } else {
                showLoadFailure("Не нашёл пары на сегодня. Возможно, сегодня занятий нет.");
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
                "var esc=today.replace(/\\./g,'\\\\.');" +
                "var re=new RegExp('(?:^|\\\\n)\\\\s*'+esc+'\\\\s+(?:'+days+')(?:\\\\s*\\\\(сегодня\\\\))?','i');" +
                "var m=re.exec(body);" +
                "if(!m){re=new RegExp('(?:^|\\\\n)\\\\s*'+esc+'(?:\\\\s|$)','i');m=re.exec(body);}" +
                "if(!m){return JSON.stringify({ok:false,date:today,text:'',debug:body.slice(0,1200)});}" +
                "var start=m.index;" +
                "var tail=body.slice(start);" +
                "var rest=tail.slice(Math.max(1,m[0].length));" +
                "var nextRe=new RegExp('(?:^|\\\\n)\\\\s*\\\\d{2}\\\\.\\\\d{2}\\\\.\\\\d{4}\\\\s+(?:'+days+')','i');" +
                "var next=nextRe.exec(rest);" +
                "var section=next?tail.slice(0,m[0].length+next.index):tail;" +
                "section=section.replace(/Нашли ошибку[\\s\\S]*$/i,'').trim();" +
                "return JSON.stringify({ok:true,date:today,text:section,debug:body.slice(0,800)});" +
                "})()";
    }

    private boolean looksLikeSchedule(String text) {
        String lower = text.toLowerCase(ru);
        return lower.contains("пара") || lower.contains("учебный корпус") || lower.contains("преп.") || lower.contains("ауд.");
    }

    private ScheduleResult parseSchedule(String displayDate, String section) {
        String clean = cleanupSection(section);
        List<String> pairs = splitPairs(clean);

        if (pairs.isEmpty()) {
            if (looksLikeSchedule(clean)) {
                pairs.add(clean);
            } else {
                pairs.add("На сегодня пары не найдены. Возможно, сегодня занятий нет или сайт временно отдал пустое расписание.");
            }
        }
        return new ScheduleResult(displayDate, clean, pairs);
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
        titleView.setText("Расписание");
        dateView.setText("Сегодня • " + result.displayDate);
        List<String> visiblePairs = filterPairsForSubgroup(result.pairs);
        summaryView.setText(summaryText(visiblePairs.size()) + " • " + subgroupDisplay());
        listLayout.removeAllViews();
        if (visiblePairs.isEmpty()) {
            addMessageCard("Пар не найдено", "Для выбранной подгруппы на сегодня пары не найдены. Можно переключиться на «Все», чтобы проверить полное расписание.");
        } else {
            for (String pair : visiblePairs) {
                addPairCard(pair);
            }
        }
        if (!fromCache) {
            statusView.setText("Обновлено: " + nowString());
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

    private String summaryText(int pairCount) {
        if (pairCount <= 0) return "Сегодня пары не найдены";
        if (pairCount == 1) return "Сегодня 1 пара";
        if (pairCount >= 2 && pairCount <= 4) return "Сегодня " + pairCount + " пары";
        return "Сегодня " + pairCount + " пар";
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
                    darkMode ? Color.rgb(134, 224, 158) : Color.rgb(28, 110, 47),
                    darkMode ? Color.rgb(245, 255, 247) : Color.rgb(19, 78, 34));
        }
        if (!building.isEmpty()) {
            addLocationCard(box, "Корпус", formatBuildingValue(building), buildingChipBg, buildingChipStroke,
                    darkMode ? Color.rgb(158, 238, 177) : Color.rgb(24, 101, 45),
                    buildingChipText);
        }
    }

    private void addLocationCard(LinearLayout parent, String label, String value, int bgColor, int strokeColor, int labelColor, int valueColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(0, 0, dp(12), 0);
        card.setBackground(locationBackground(bgColor, strokeColor));

        View accent = new View(this);
        accent.setBackgroundColor(labelColor);
        card.addView(accent, new LinearLayout.LayoutParams(dp(5), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setPadding(dp(12), dp(8), 0, dp(8));
        card.addView(textBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView labelView = new TextView(this);
        labelView.setText(label.toUpperCase(ru));
        labelView.setTextColor(labelColor);
        labelView.setTextSize(11);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setLetterSpacing(0.04f);
        textBox.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(valueColor);
        valueView.setTextSize(17);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setSingleLine(false);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.setMargins(0, dp(2), 0, 0);
        textBox.addView(valueView, valueParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        parent.addView(card, params);
    }

    private GradientDrawable locationBackground(int color, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(13));
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

    private String todayString() {
        return new SimpleDateFormat("dd.MM.yyyy", ru).format(Calendar.getInstance().getTime());
    }

    private String nowString() {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", ru).format(Calendar.getInstance().getTime());
    }

    private static class ScheduleResult {
        final String displayDate;
        final String fullText;
        final List<String> pairs;

        ScheduleResult(String displayDate, String fullText, List<String> pairs) {
            this.displayDate = displayDate;
            this.fullText = fullText;
            this.pairs = pairs;
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
