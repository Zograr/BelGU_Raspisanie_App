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
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String PREFS = "schedule_cache";
    private static final String KEY_TEXT = "last_text";
    private static final String KEY_DATE = "last_date";
    private static final String KEY_UPDATED = "last_updated";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout listLayout;
    private TextView statusView;
    private TextView titleView;
    private ProgressBar progressBar;
    private Button refreshButton;
    private Button openSiteButton;

    private String scheduleUrl;
    private final Locale ru = new Locale("ru", "RU");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scheduleUrl = getString(R.string.schedule_url);
        buildUi();
        loadCachedSchedule();
        refreshSchedule();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
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

        refreshButton.setOnClickListener(v -> refreshSchedule());
        openSiteButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(scheduleUrl)));
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshSchedule() {
        setLoading(true);
        statusView.setText("Загружаю расписание с сайта БелГУ...");
        executor.execute(() -> {
            try {
                String html = download(scheduleUrl);
                ScheduleResult result = parseSchedule(html);
                saveCache(result.displayDate, result.fullText);
                main.post(() -> showSchedule(result, false));
            } catch (Exception e) {
                main.post(() -> {
                    setLoading(false);
                    SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                    String cached = prefs.getString(KEY_TEXT, "");
                    String date = prefs.getString(KEY_DATE, todayString());
                    String updated = prefs.getString(KEY_UPDATED, "");
                    if (!cached.isEmpty()) {
                        ScheduleResult cachedResult = new ScheduleResult(date, cached, splitPairs(cached));
                        statusView.setText("Не удалось обновить. Показана сохранённая версия" + (updated.isEmpty() ? "." : " от " + updated + "."));
                        showSchedule(cachedResult, true);
                    } else {
                        listLayout.removeAllViews();
                        addMessageCard("Не удалось загрузить расписание", "Проверь интернет или открой сайт кнопкой сверху.\n\nОшибка: " + e.getMessage());
                        statusView.setText("Ошибка загрузки");
                    }
                });
            }
        });
    }

    private String download(String urlString) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 Android BelGUSchedule/1.0");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

            int code = connection.getResponseCode();
            InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (input == null) throw new Exception("HTTP " + code);

            String charset = getCharset(connection.getContentType());
            BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(input), charset));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            reader.close();
            if (code >= 400) throw new Exception("HTTP " + code);
            return builder.toString();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String getCharset(String contentType) {
        if (contentType != null) {
            Matcher m = Pattern.compile("charset=([^;]+)", Pattern.CASE_INSENSITIVE).matcher(contentType);
            if (m.find()) return m.group(1).trim().replace("\"", "");
        }
        return "UTF-8";
    }

    private ScheduleResult parseSchedule(String html) {
        String today = todayString();
        String text = htmlToPlainText(html);
        String section = extractTodaySection(text, today);
        String displayDate = today;

        if (section.trim().isEmpty()) {
            section = "На сегодня пары не найдены. Возможно, выходной или сайт поменял структуру.";
        }

        // Убираем лишние строки сайта, если они попали в секцию.
        section = section.replaceAll("(?i)Нашли ошибку[\\s\\S]*$", "").trim();
        section = section.replaceAll("(?m)^\\s*Группа\\s+90002595\\s*$", "").trim();

        List<String> pairs = splitPairs(section);
        if (pairs.isEmpty()) {
            pairs.add(section);
        }
        return new ScheduleResult(displayDate, section, pairs);
    }

    private String htmlToPlainText(String html) {
        String text = html;
        text = text.replaceAll("(?i)<script[\\s\\S]*?</script>", " ");
        text = text.replaceAll("(?i)<style[\\s\\S]*?</style>", " ");
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</(tr|td|th|div|p|li|h1|h2|h3|h4|h5|h6|option|select|table|tbody|thead)>", "\n");
        text = text.replaceAll("<[^>]+>", " ");
        text = decodeHtml(text);
        text = text.replace('\u00A0', ' ');
        text = text.replace('–', '-').replace('—', '-');
        text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        text = text.replaceAll("(?m)^\\s+|\\s+$", "");
        text = text.replaceAll("\\n{2,}", "\n");
        return text.trim();
    }

    private String decodeHtml(String text) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString();
            } else {
                return Html.fromHtml(text).toString();
            }
        } catch (Exception ignored) {
            return text
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"");
        }
    }

    private String extractTodaySection(String text, String today) {
        Pattern datePattern = Pattern.compile("\\b\\d{2}\\.\\d{2}\\.\\d{4}\\b");
        Matcher matcher = datePattern.matcher(text);
        List<Integer> starts = new ArrayList<>();
        List<String> dates = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
            dates.add(matcher.group());
        }
        for (int i = 0; i < dates.size(); i++) {
            if (today.equals(dates.get(i))) {
                int start = starts.get(i);
                int end = text.length();
                if (i + 1 < starts.size()) end = starts.get(i + 1);
                return text.substring(start, end).trim();
            }
        }

        // Запасной вариант для мобильной версии, если она отдаёт только один день без полного списка дат.
        if (text.toLowerCase(ru).contains("сегодня")) {
            int start = Math.max(0, text.toLowerCase(ru).indexOf("сегодня") - 80);
            int end = text.length();
            return text.substring(start, end).trim();
        }
        return "";
    }

    private List<String> splitPairs(String section) {
        List<String> pairs = new ArrayList<>();
        String oneLine = section.replace('\n', ' ');
        oneLine = oneLine.replaceAll("\\s+", " ").trim();

        Pattern pairPattern = Pattern.compile("(\\d\\s*пара)\\s+(\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2})(.*?)(?=\\s+\\d\\s*пара\\s+\\d{1,2}:\\d{2}|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pairPattern.matcher(oneLine);
        while (matcher.find()) {
            String pairNumber = matcher.group(1).replaceAll("\\s+", " ").trim();
            String time = matcher.group(2).replaceAll("\\s*[-]\\s*", " - ").trim();
            String body = matcher.group(3).trim();

            body = body.replaceAll("(?i)\\bпреп\\.", "\nПреп.: ");
            body = body.replaceAll("(?i)\\bауд\\.", "\nАуд.: ");
            body = body.replaceAll("(?i)Учебный корпус", "учебный корпус");
            body = body.replaceAll("\\s+", " ");
            body = body.replace(" Преп.: ", "\nПреп.: ").replace(" Ауд.: ", "\nАуд.: ");

            String formatted = pairNumber + "  •  " + time;
            if (!body.isEmpty()) formatted += "\n" + body.trim();
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
            ScheduleResult result = new ScheduleResult(date, cached, splitPairs(cached));
            if (result.pairs.isEmpty()) result.pairs.add(cached);
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
