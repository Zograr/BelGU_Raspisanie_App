package ru.zograr.belguschedule;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
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
    private static final int MAX_EXTRACT_ATTEMPTS = 4;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Locale ru = new Locale("ru", "RU");

    private LinearLayout listLayout;
    private TextView statusView;
    private TextView titleView;
    private ProgressBar progressBar;
    private Button refreshButton;
    private Button openSiteButton;
    private WebView hiddenWebView;

    private String scheduleUrl;
    private boolean mainFrameError = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scheduleUrl = getString(R.string.schedule_url);
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 245));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(14));
        header.setBackgroundColor(Color.rgb(11, 79, 19));
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        titleView = new TextView(this);
        titleView.setText("Расписание на сегодня");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(24);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(titleView);

        TextView groupView = new TextView(this);
        groupView.setText("Группа 90002595");
        groupView.setTextColor(Color.rgb(220, 245, 220));
        groupView.setTextSize(15);
        groupView.setPadding(0, dp(4), 0, 0);
        header.addView(groupView);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(12), dp(10), dp(12), dp(8));
        root.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        refreshButton = new Button(this);
        refreshButton.setText("Обновить");
        refreshButton.setAllCaps(false);
        controls.addView(refreshButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        openSiteButton = new Button(this);
        openSiteButton.setText("Сайт");
        openSiteButton.setAllCaps(false);
        LinearLayout.LayoutParams siteParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        siteParams.setMargins(dp(8), 0, 0, 0);
        controls.addView(openSiteButton, siteParams);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        progressParams.setMargins(dp(8), 0, 0, 0);
        controls.addView(progressBar, progressParams);

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(90, 90, 90));
        statusView.setTextSize(13);
        statusView.setPadding(dp(18), 0, dp(18), dp(8));
        root.addView(statusView);

        ScrollView scrollView = new ScrollView(this);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(dp(12), dp(4), dp(12), dp(18));
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
        text = text.replaceAll("(?m)^\\s*Группа\\s+90002595\\s*$", "");
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
        setLoading(false);
        titleView.setText("Расписание на " + result.displayDate);
        listLayout.removeAllViews();
        for (String pair : result.pairs) {
            addPairCard(pair);
        }
        if (!fromCache) {
            statusView.setText("Обновлено: " + nowString());
        }
    }

    private void loadCachedSchedule() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String cached = prefs.getString(KEY_TEXT, "");
        if (!cached.isEmpty()) {
            String date = prefs.getString(KEY_DATE, todayString());
            ScheduleResult result = parseSchedule(date, cached);
            showSchedule(result, true);
            String updated = prefs.getString(KEY_UPDATED, "");
            statusView.setText(updated.isEmpty() ? "Показана сохранённая версия." : "Показана сохранённая версия от " + updated + ".");
        }
    }

    private void saveCache(String date, String text) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_DATE, date)
                .putString(KEY_TEXT, text)
                .putString(KEY_UPDATED, nowString())
                .apply();
    }

    private void showLoadFailure(String message) {
        setLoading(false);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String cached = prefs.getString(KEY_TEXT, "");
        String date = prefs.getString(KEY_DATE, todayString());
        String updated = prefs.getString(KEY_UPDATED, "");

        if (!cached.isEmpty()) {
            ScheduleResult cachedResult = parseSchedule(date, cached);
            showSchedule(cachedResult, true);
            statusView.setText("Не удалось обновить. Показана сохранённая версия" + (updated.isEmpty() ? "." : " от " + updated + "."));
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } else {
            listLayout.removeAllViews();
            addMessageCard("Не удалось загрузить расписание", message + "\n\nНажми «Сайт», чтобы открыть расписание вручную.");
            statusView.setText("Ошибка загрузки");
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(!loading);
    }

    private void addPairCard(String text) {
        TextView card = new TextView(this);
        card.setText(text);
        card.setTextColor(Color.rgb(25, 25, 25));
        card.setTextSize(17);
        card.setLineSpacing(dp(2), 1.0f);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(cardBackground(Color.WHITE));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        listLayout.addView(card, params);
    }

    private void addMessageCard(String title, String message) {
        TextView card = new TextView(this);
        card.setText(title + "\n\n" + message);
        card.setTextColor(Color.rgb(25, 25, 25));
        card.setTextSize(16);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(cardBackground(Color.WHITE));
        listLayout.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private GradientDrawable cardBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), Color.rgb(225, 230, 225));
        return drawable;
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
}
