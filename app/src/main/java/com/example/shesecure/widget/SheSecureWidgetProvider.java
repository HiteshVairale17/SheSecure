package com.example.shesecure.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.shesecure.auth.SessionManager;
import com.example.shesecure.auth.WelcomeActivity;
import com.example.shesecure.contacts.EmergencyContactsActivity;
import com.example.shesecure.data.local.LocalContactCache;
import com.example.shesecure.emergency.SosCountdownActivity;
import com.example.shesecure.models.EmergencyContact;

import java.util.List;

public class SheSecureWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sos);

        SessionManager sessionManager = SessionManager.getInstance(context);
        LocalContactCache contactCache = LocalContactCache.getInstance(context);
        List<EmergencyContact> contacts = contactCache.getContacts();

        boolean isLoggedIn = sessionManager.isLoggedIn();
        boolean hasContacts = !contacts.isEmpty();

        Intent intent;
        String statusText;
        String subtext;
        int statusColor;

        if (!isLoggedIn) {
            statusText = context.getString(R.string.widget_status_signed_out);
            subtext = context.getString(R.string.widget_subtext_signed_out);
            statusColor = ContextCompat.getColor(context, R.color.status_warning_amber);
            intent = new Intent(context, WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        } else if (!hasContacts) {
            statusText = context.getString(R.string.widget_status_unconfigured);
            subtext = context.getString(R.string.widget_subtext_unconfigured);
            statusColor = ContextCompat.getColor(context, R.color.status_warning_amber);
            intent = new Intent(context, EmergencyContactsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        } else {
            statusText = context.getString(R.string.widget_status_ready, contacts.size());
            subtext = context.getString(R.string.widget_subtext_ready);
            statusColor = ContextCompat.getColor(context, R.color.status_active_green);
            intent = new Intent(context, SosCountdownActivity.class);
            intent.putExtra(SosCountdownActivity.EXTRA_TRIGGER_SOURCE, SosCountdownActivity.SOURCE_WIDGET);
            intent.putExtra(SosCountdownActivity.EXTRA_IS_TEST_MODE, false);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }

        views.setTextViewText(R.id.tv_widget_status, statusText);
        views.setTextColor(R.id.tv_widget_status, statusColor);
        views.setTextViewText(R.id.tv_widget_subtext, subtext);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(R.id.btn_widget_sos, pendingIntent);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    public static void updateAllWidgets(Context context) {
        try {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, SheSecureWidgetProvider.class);
            int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            if (allWidgetIds != null && allWidgetIds.length > 0) {
                for (int widgetId : allWidgetIds) {
                    updateAppWidget(context, appWidgetManager, widgetId);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
