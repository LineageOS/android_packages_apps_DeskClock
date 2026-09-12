/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock.data;

import static com.android.deskclock.NotificationUtils.STOPWATCH_NOTIFICATION_CHANNEL_ID;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.text.format.DateUtils;

import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.NotificationCompat.Builder;

import com.android.deskclock.DeskClock;
import com.android.deskclock.NotificationUtils;
import com.android.deskclock.R;
import com.android.deskclock.ThemeUtils;
import com.android.deskclock.Utils;
import com.android.deskclock.events.Events;
import com.android.deskclock.stopwatch.StopwatchService;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds notification to reflect the latest state of the stopwatch and recorded laps.
 */
class StopwatchNotificationBuilder {

    public Notification build(Context context, NotificationModel nm, Stopwatch stopwatch) {
        @StringRes final int eventLabel = R.string.label_notification;

        // Intent to load the app when the notification is tapped.
        final Intent showApp = new Intent(context, DeskClock.class)
                .setAction(StopwatchService.ACTION_SHOW_STOPWATCH)
                .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

        final PendingIntent pendingShowApp = Utils.pendingActivityIntent(context, showApp);

        // Compute some values required below.
        final boolean running = stopwatch.isRunning();
        final Resources res = context.getResources();
        final long totalTime = stopwatch.getTotalTime();

        // Time at which the stopwatch reached 0; the platform derives the chronometer from it.
        final long base = System.currentTimeMillis() - totalTime;

        final List<Action> actions = new ArrayList<>(2);
        final CharSequence text;
        final String criticalText;

        if (running) {
            // Left button: Pause
            final Intent pause = new Intent(context, StopwatchService.class)
                    .setAction(StopwatchService.ACTION_PAUSE_STOPWATCH)
                    .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

            final CharSequence title1 = res.getText(R.string.sw_pause_button);
            final PendingIntent intent1 = Utils.pendingServiceIntent(context, pause);
            actions.add(new Action.Builder(null, title1, intent1).build());

            // Right button: Add Lap
            if (DataModel.getDataModel().canAddMoreLaps()) {
                final Intent lap = new Intent(context, StopwatchService.class)
                        .setAction(StopwatchService.ACTION_LAP_STOPWATCH)
                        .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

                final CharSequence title2 = res.getText(R.string.sw_lap_button);
                final PendingIntent intent2 = Utils.pendingServiceIntent(context, lap);
                actions.add(new Action.Builder(null, title2, intent2).build());
            }

            // Show the current lap number if any laps have been recorded.
            final int lapCount = DataModel.getDataModel().getLaps().size();
            if (lapCount > 0) {
                text = res.getString(R.string.sw_notification_lap_number, lapCount + 1);
            } else {
                text = null;
            }

            // Leave the chip to the chronometer; it takes precedence over critical text.
            criticalText = null;
        } else {
            // Left button: Start
            final Intent start = new Intent(context, StopwatchService.class)
                    .setAction(StopwatchService.ACTION_START_STOPWATCH)
                    .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

            // We can only resume from the notification.
            final CharSequence title1 = res.getText(R.string.sw_resume_button);
            final PendingIntent intent1 = Utils.pendingServiceIntent(context, start);
            actions.add(new Action.Builder(null, title1, intent1).build());

            // Right button: Reset (dismisses notification and resets stopwatch)
            final Intent reset = new Intent(context, StopwatchService.class)
                    .setAction(StopwatchService.ACTION_RESET_STOPWATCH)
                    .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

            final CharSequence title2 = res.getText(R.string.sw_reset_button);
            final PendingIntent intent2 = Utils.pendingServiceIntent(context, reset);
            actions.add(new Action.Builder(null, title2, intent2).build());

            // The chronometer cannot be stopped, so report the frozen time as text instead. The
            // chip has no other source of content while paused, so give it the same time.
            criticalText = DateUtils.formatElapsedTime(totalTime / DateUtils.SECOND_IN_MILLIS);
            text = criticalText;
        }

        final Builder notification = new NotificationCompat.Builder(
                context, STOPWATCH_NOTIFICATION_CHANNEL_ID)
                        .setLocalOnly(true)
                        .setOngoing(true)
                        .setWhen(base)
                        .setShowWhen(false)
                        .setUsesChronometer(running)
                        .setRequestPromotedOngoing(true)
                        .setShortCriticalText(criticalText)
                        .setContentTitle(res.getText(R.string.menu_stopwatch))
                        .setContentText(text)
                        .setSubText(running ? null : res.getText(R.string.swn_paused))
                        .setContentIntent(pendingShowApp)
                        .setAutoCancel(stopwatch.isPaused())
                        .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                        .setSmallIcon(R.drawable.stat_notify_stopwatch)
                        .setColor(ThemeUtils.resolveColor(context, R.attr.colorSurface))
                        .setGroup(nm.getStopwatchNotificationGroupKey());

        for (Action action : actions) {
            notification.addAction(action);
        }

        NotificationUtils.createChannel(context, STOPWATCH_NOTIFICATION_CHANNEL_ID);
        return notification.build();
    }
}
