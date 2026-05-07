package ru.zograr.belguschedule;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import java.util.Calendar;

public class DailyUpdateReceiver extends BroadcastReceiver {
    private static final String ACTION_DAILY_UPDATE = "ru.zograr.belguschedule.ACTION_DAILY_UPDATE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_DAILY_UPDATE.equals(intent.getAction())) return;

        final PendingResult pendingResult = goAsync();
        new Thread(() -> {
            try {
                String group = ScheduleDataHelper.group(context);
                String text = ScheduleDataHelper.fetchScheduleText(group);
                if (ScheduleDataHelper.looksLikeSchedule(text)) {
                    ScheduleDataHelper.saveFetchedSchedule(context, group, text);
                }
                ScheduleWidgetProvider.updateAllWidgets(context);
            } catch (Exception ignored) {
                ScheduleWidgetProvider.updateAllWidgets(context);
            } finally {
                scheduleDailyUpdate(context);
                pendingResult.finish();
            }
        }).start();
    }

    static void scheduleDailyUpdate(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            Intent intent = new Intent(context, DailyUpdateReceiver.class);
            intent.setAction(ACTION_DAILY_UPDATE);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    700,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 7);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }

            alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
            );
        } catch (Exception ignored) {
        }
    }
}
