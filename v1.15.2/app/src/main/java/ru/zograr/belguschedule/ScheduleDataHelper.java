package ru.zograr.belguschedule;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;

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

class ScheduleDataHelper {
    static final String PREFS = "schedule_cache";
    static final String KEY_TEXT = "last_text";
    static final String KEY_DATE = "last_date";
    static final String KEY_UPDATED = "last_updated";
    static final String KEY_GROUP = "selected_group";
    static final String KEY_SUBGROUP = "selected_subgroup";
    static final String DEFAULT_GROUP = "90002595";
    static final String KEY_AUTO_UPDATE_HOUR = "auto_update_hour";
    static final String KEY_AUTO_UPDATE_MINUTE = "auto_update_minute";
    static final String SUBGROUP_ALL = "all";
    static final String SUBGROUP_1 = "1";
    static final String SUBGROUP_2 = "2";
    static final String SCHEDULE_BASE_URL = "https://bsuedu.ru/bsu/education/schedule/groups/index.php?group=";

    private static final Locale RU = new Locale("ru", "RU");

    static String group(Context context) {
        return prefs(context).getString(KEY_GROUP, DEFAULT_GROUP);
    }

    static String subgroup(Context context) {
        return prefs(context).getString(KEY_SUBGROUP, SUBGROUP_ALL);
    }

    static String cacheKey(String key, String group) {
        return key + "_" + group + "_week";
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static WidgetPair nearestPair(Context context) {
        String group = group(context);
        SharedPreferences prefs = prefs(context);
        String cached = prefs.getString(cacheKey(KEY_TEXT, group), "");
        String updated = prefs.getString(cacheKey(KEY_UPDATED, group), "");

        if (cached.trim().isEmpty()) {
            return new WidgetPair("Нет данных", "Открой приложение", "Расписание ещё не загружено", "Группа " + group, autoUpdateText(context));
        }

        List<DaySchedule> days = splitDays(cleanupSection(cached));
        DaySchedule today = findToday(days);
        if (today == null && days.isEmpty()) {
            today = new DaySchedule(todayString(), "Сегодня", cached, splitPairs(cached), true);
        }

        if (today == null) {
            return new WidgetPair("Сегодня", "Пар не найдено", "В расписании нет сегодняшнего дня", "Группа " + group, updatedText(updated));
        }

        List<String> pairs = filterPairsForSubgroup(today.pairs, subgroup(context));
        if (pairs.isEmpty()) {
            return new WidgetPair("Сегодня", "Пар нет", "Для выбранной подгруппы пар нет", "Группа " + group, updatedText(updated));
        }

        int now = minutesNow();
        PairInfo firstFuture = null;
        PairInfo current = null;
        for (String pair : pairs) {
            PairInfo info = parsePair(pair);
            if (!info.hasTime()) continue;

            if (now >= info.start && now <= info.end) {
                current = info;
                break;
            }

            if (info.start > now && firstFuture == null) {
                firstFuture = info;
            }
        }

        PairInfo chosen = current != null ? current : firstFuture;
        if (chosen == null) {
            PairInfo last = parsePair(pairs.get(pairs.size() - 1));
            return new WidgetPair("Пары закончились", last.time, last.lesson, last.place, updatedText(updated));
        }

        String status = current != null ? "Сейчас идёт" : "Следующая пара";
        return new WidgetPair(status, chosen.time, chosen.lesson, chosen.place, updatedText(updated));
    }

    static String fetchScheduleText(String group) throws Exception {
        URL url = new URL(SCHEDULE_BASE_URL + group);
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
        StringBuilder html = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            html.append(line).append('\n');
        }
        reader.close();

        String prepared = html.toString()
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</tr>", "\n")
                .replaceAll("(?i)</td>", " ")
                .replaceAll("(?i)</th>", " ")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</div>", "\n");

        String plain;
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            plain = Html.fromHtml(prepared, Html.FROM_HTML_MODE_LEGACY).toString();
        } else {
            plain = Html.fromHtml(prepared).toString();
        }

        return cleanupSection(plain);
    }

    static void saveFetchedSchedule(Context context, String group, String text) {
        prefs(context).edit()
                .putString(cacheKey(KEY_DATE, group), todayString())
                .putString(cacheKey(KEY_TEXT, group), text)
                .putString(cacheKey(KEY_UPDATED, group), nowString())
                .apply();
    }

    static boolean looksLikeSchedule(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(RU);
        return lower.contains("пара") && (lower.contains("лек.") || lower.contains("пр.з.") || lower.contains("лаб."));
    }

    static String updatedText(String updated) {
        return updated == null || updated.trim().isEmpty() ? "автообновление включено" : "обновлено " + updated;
    }

    static String autoUpdateText(Context context) {
        int hour = prefs(context).getInt(KEY_AUTO_UPDATE_HOUR, 7);
        int minute = prefs(context).getInt(KEY_AUTO_UPDATE_MINUTE, 0);
        return String.format(RU, "автообновление %02d:%02d", hour, minute);
    }

    static List<DaySchedule> splitDays(String fullWeekText) {
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

    static DaySchedule findToday(List<DaySchedule> days) {
        String today = todayString();
        for (DaySchedule day : days) {
            if (today.equals(day.displayDate)) return day;
        }

        for (DaySchedule day : days) {
            if (day.isToday && today.equals(day.displayDate)) return day;
        }

        return null;
    }

    static List<String> splitPairs(String sectionText) {
        List<String> pairs = new ArrayList<>();
        String normalized = sectionText.replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n+", "\n");

        Pattern pairPattern = Pattern.compile(
                "(?is)(\\d+\\s*пара)\\s+(\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2})(.*?)(?=\\n?\\s*\\d+\\s*пара\\s+\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}|$)"
        );
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

    static List<String> filterPairsForSubgroup(List<String> pairs, String subgroup) {
        List<String> visible = new ArrayList<>();
        if (SUBGROUP_ALL.equals(subgroup)) {
            for (String pair : pairs) {
                visible.addAll(splitPairForAllSubgroups(pair));
            }
            return visible;
        }
        for (String pair : pairs) {
            String filtered = filterPairForSubgroup(pair, subgroup);
            if (!filtered.isEmpty()) {
                visible.add(filtered);
            }
        }
        return visible;
    }

    static List<String> splitPairForAllSubgroups(String pair) {
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

    static String filterPairForSubgroup(String pair, String subgroup) {
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

    static boolean isRawLessonLine(String line) {
        String lower = line.trim().toLowerCase(RU);
        return lower.startsWith("лек.") || lower.startsWith("пр.з.") || lower.startsWith("лаб.");
    }

    static String extractSubgroupFromLine(String line) {
        Matcher direct = Pattern.compile("(?i)\\b([12])\\s*подгруппа\\b").matcher(line);
        if (direct.find()) return direct.group(1);

        Matcher shortForm = Pattern.compile("(?i)п/г\\s*([12])\\b").matcher(line);
        if (shortForm.find()) return shortForm.group(1);

        return null;
    }

    static PairInfo parsePair(String text) {
        String[] parts = text.split("\\n");
        String first = parts.length > 0 ? parts[0].trim() : "Пара";
        String pairName = first;
        String time = "";
        if (first.contains("•")) {
            String[] head = first.split("•", 2);
            pairName = head[0].trim();
            time = head[1].trim();
        }

        String lesson = "Пара";
        String place = "Аудитория не указана";

        for (int i = 1; i < parts.length; i++) {
            String pretty = prettifyLessonLine(parts[i]);
            if (pretty.startsWith("Аудитория:")) {
                place = pretty.replaceFirst("(?i)^Аудитория:\\s*", "").trim();
                if (isHorkinaPlace(place)) place = "Хоркина";
            } else if (!pretty.startsWith("Преподаватель:") && lesson.equals("Пара")) {
                lesson = pretty;
            }
        }

        int start = -1;
        int end = -1;
        Matcher matcher = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})").matcher(time);
        if (matcher.find()) {
            start = Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2));
            end = Integer.parseInt(matcher.group(3)) * 60 + Integer.parseInt(matcher.group(4));
        }

        return new PairInfo(pairName, time, lesson, place, start, end);
    }

    static String prettifyLessonLine(String line) {
        String clean = line.trim().replaceAll("\\s+", " ");
        clean = clean.replace("Преп.:", "Преподаватель:");
        clean = clean.replace("Ауд.:", "Аудитория:");
        clean = clean.replace("Аудитория:", "Аудитория: ").replaceAll("Аудитория:\\s+", "Аудитория: ");
        clean = clean.replace("Преподаватель:", "Преподаватель: ").replaceAll("Преподаватель:\\s+", "Преподаватель: ");

        Matcher practice = Pattern.compile("(?i)^(пр\\.з\\.|лаб\\.)\\s*\\(п/г\\s*([^)]*)\\)\\s*(.+)$").matcher(clean);
        if (practice.find()) {
            String type = practice.group(1).toLowerCase(RU).startsWith("лаб") ? "Лабораторная" : "Практика";
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

    static boolean isHorkinaPlace(String place) {
        String lower = place.toLowerCase(RU);
        return lower.contains("универсальный") && lower.contains("спортив")
                || lower.contains("уск")
                || lower.contains("хоркин");
    }

    static String cleanupSection(String text) {
        if (text == null) return "";

        text = text.replace('\u00A0', ' ');
        text = text.replaceAll("(?i)(?<!\\n)(\\d{2}\\.\\d{2}\\.\\d{4}\\s+(?:Понедельник|Вторник|Среда|Четверг|Пятница|Суббота|Воскресенье))", "\n$1");
        text = text.replaceAll("(?i)Нашли ошибку[\\s\\S]*$", "");
        text = text.replaceAll("(?m)^\\s*Ссылка на расписание этой группы\\s*$", "");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\\n\\s+", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    static String todayString() {
        return new SimpleDateFormat("dd.MM.yyyy", RU).format(Calendar.getInstance().getTime());
    }

    static String nowString() {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", RU).format(Calendar.getInstance().getTime());
    }

    static int minutesNow() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    static class DaySchedule {
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

    static class PairInfo {
        final String pairName;
        final String time;
        final String lesson;
        final String place;
        final int start;
        final int end;

        PairInfo(String pairName, String time, String lesson, String place, int start, int end) {
            this.pairName = pairName;
            this.time = time;
            this.lesson = lesson;
            this.place = place;
            this.start = start;
            this.end = end;
        }

        boolean hasTime() {
            return start >= 0 && end >= 0;
        }
    }

    static class WidgetPair {
        final String status;
        final String time;
        final String lesson;
        final String place;
        final String updated;

        WidgetPair(String status, String time, String lesson, String place, String updated) {
            this.status = status;
            this.time = time;
            this.lesson = lesson;
            this.place = place;
            this.updated = updated;
        }
    }
}
