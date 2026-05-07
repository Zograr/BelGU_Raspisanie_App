package ru.zograr.belguschedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class ScheduleWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        updateWidgets(context, manager, appWidgetIds);
        DailyUpdateReceiver.scheduleDailyUpdate(context);
    }

    @Override
    public void onEnabled(Context context) {
        DailyUpdateReceiver.scheduleDailyUpdate(context);
    }

    static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, ScheduleWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        updateWidgets(context, manager, ids);
    }

    private static void updateWidgets(Context context, AppWidgetManager manager, int[] ids) {
        if (ids == null || ids.length == 0) return;

        ScheduleDataHelper.WidgetPair pair = ScheduleDataHelper.nearestPair(context);

        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_next_pair);
            views.setTextViewText(R.id.widget_status, pair.status);
            views.setTextViewText(R.id.widget_time, pair.time);
            views.setTextViewText(R.id.widget_lesson, pair.lesson);
            views.setTextViewText(R.id.widget_place, pair.place);
            views.setTextViewText(R.id.widget_updated, pair.updated);

            Intent intent = new Intent(context, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    100 + id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

            manager.updateAppWidget(id, views);
        }
    }
}
