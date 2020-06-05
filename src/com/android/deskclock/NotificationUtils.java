/*
 * Copyright (C) 2020 The LineageOS Project
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

package com.android.deskclock;

import android.app.NotificationChannel;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationManagerCompat;

import com.android.deskclock.alarms.AlarmNotifications;
import com.android.deskclock.data.StopwatchNotificationBuilder;

public class NotificationUtils {
    // Creates a notification if applicable
    public static void createChannelIfRequired(NotificationManagerCompat nm,
                                                String id, CharSequence name, int importance) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(id, name, importance);
        nm.createNotificationChannel(channel);
    }

    public static void deleteChannel(NotificationManagerCompat nm, String channelId) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = nm.getNotificationChannel(channelId);
        if (channel != null) {
            nm.deleteNotificationChannel(channelId);
        }
    }

    private static void updateChannelDetails(NotificationManagerCompat nm,
            String id, CharSequence name, int importance) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = nm.getNotificationChannel(id);
        if (channel != null) {
            // Calling create on an existing id updates the details
            createChannelIfRequired(nm, id, name, importance);
        }
    }

    public static void updateNotificationChannels(Context context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        // These channels got a new behavior so we need to recreate them with new ids
        deleteChannel(nm, AlarmNotifications.ALARM_LOW_PRIORITY_NOTIFICATION_CHANNEL_ID);
        deleteChannel(nm, AlarmNotifications.ALARM_HIGH_PRIORITY_NOTIFICATION_CHANNEL_ID);
        deleteChannel(nm, StopwatchNotificationBuilder.STOPWATCH_NOTIFICATION_CHANNEL_ID);

        // We gave new titles to these. Force-update the channels by triggering creation

        updateChannelDetails(nm, AlarmNotifications.ALARM_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.alarm_notification),
                NotificationManagerCompat.IMPORTANCE_HIGH);
        updateChannelDetails(nm, AlarmNotifications.ALARM_MISSED_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.alarm_missed_notification),
                NotificationManagerCompat.IMPORTANCE_HIGH);
        updateChannelDetails(nm, AlarmNotifications.ALARM_SNOOZE_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.alarm_snooze_notification),
                NotificationManagerCompat.IMPORTANCE_HIGH);
    }
}