/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.alarmclock;

import static android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.Intent.ACTION_LOCALE_CHANGED;
import static android.content.Intent.ACTION_TIMEZONE_CHANGED;
import static android.content.Intent.ACTION_TIME_CHANGED;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.deskclock.DeskClock;
import com.android.deskclock.LogUtils;
import com.android.deskclock.R;
import com.android.deskclock.Utils;
import com.android.deskclock.data.DataModel;

import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * <p>This provider produces a widget resembling one of the formats below.</p>
 *
 * If an alarm is scheduled to ring in the future:
 * <pre>
 *         12:59 AM
 * WED, FEB 3 ⏰ THU 9:30 AM
 * </pre>
 *
 * If no alarm is scheduled to ring in the future:
 * <pre>
 *         12:59 AM
 *        WED, FEB 3
 * </pre>
 *
 * This widget is scaling the font sizes to fit within the widget bounds chosen by the user without
 * any clipping. To do so it measures layouts offscreen using a range of font sizes in order to
 * choose optimal values.
 */
public class LineageAppWidgetProvider extends AppWidgetProvider {

    private static final LogUtils.Logger LOGGER = new LogUtils.Logger("LineageWidgetProvider");

    private static boolean sReceiversRegistered;

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        LOGGER.i("onReceive: " + intent);
        super.onReceive(context, intent);

        final AppWidgetManager wm = AppWidgetManager.getInstance(context);
        if (wm == null) {
            return;
        }

        final ComponentName provider = new ComponentName(context, getClass());
        final int[] widgetIds = wm.getAppWidgetIds(provider);

        final String action = intent.getAction();
        switch (action) {
            case ACTION_NEXT_ALARM_CLOCK_CHANGED:
            case ACTION_LOCALE_CHANGED:
            case ACTION_TIME_CHANGED:
            case ACTION_TIMEZONE_CHANGED:
                for (int widgetId : widgetIds) {
                    relayoutWidget(context, wm, widgetId, wm.getAppWidgetOptions(widgetId));
                }
        }
    }

    /**
     * Called when widgets must provide remote views.
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager wm, int[] widgetIds) {
        super.onUpdate(context, wm, widgetIds);

        for (int widgetId : widgetIds) {
            relayoutWidget(context, wm, widgetId, wm.getAppWidgetOptions(widgetId));
        }
    }

    /**
     * Called when the app widget changes sizes.
     */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager wm, int widgetId,
            Bundle options) {
        super.onAppWidgetOptionsChanged(context, wm, widgetId, options);

        // Scale the fonts of the clock to fit inside the new size
        relayoutWidget(context, AppWidgetManager.getInstance(context), widgetId, options);
    }

    /**
     * Compute optimal font and icon sizes offscreen for both portrait and landscape orientations
     * using the last known widget size and apply them to the widget.
     */
    private static void relayoutWidget(Context context, AppWidgetManager wm, int widgetId,
            Bundle options) {
        final RemoteViews widget = relayoutWidget(context, wm, widgetId);
        wm.updateAppWidget(widgetId, widget);
    }

    /**
     * Compute optimal font and icon sizes offscreen for the given orientation.
     */
    private static RemoteViews relayoutWidget(Context context, AppWidgetManager wm, int widgetId) {
        // Create a remote view for the Lineage clock.
        final String packageName = context.getPackageName();
        final RemoteViews rv = new RemoteViews(packageName, R.layout.lineage_widget);

        // Tapping on the widget opens the app (if not on the lock screen).
        if (Utils.isWidgetClickable(wm, widgetId)) {
            final Intent openApp = new Intent(context, DeskClock.class);
            final PendingIntent pi = PendingIntent.getActivity(context, 0, openApp, FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.lineage_widget, pi);
        }

        final String nextAlarmTime = Utils.getNextAlarmTime(context);
        if (TextUtils.isEmpty(nextAlarmTime)) {
            rv.setViewVisibility(R.id.nextAlarm, GONE);
            rv.setViewVisibility(R.id.nextAlarmIcon, GONE);
        } else  {
            rv.setTextViewText(R.id.nextAlarm, nextAlarmTime);
            rv.setViewVisibility(R.id.nextAlarm, VISIBLE);
            rv.setViewVisibility(R.id.nextAlarmIcon, VISIBLE);
        }

        return rv;
    }
}
