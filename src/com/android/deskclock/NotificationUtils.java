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
import android.util.ArraySet;
import android.util.Log;
import androidx.core.app.NotificationManagerCompat;

import com.android.deskclock.alarms.AlarmNotifications;
import com.android.deskclock.data.StopwatchNotificationBuilder;
import com.android.deskclock.data.TimerNotificationBuilder;

import java.util.Set;

public class NotificationUtils {

    private static final String TAG = NotificationUtils.class.getSimpleName();

    public static void createChannel(Context context, NotificationManagerCompat nm, String id) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }

        int nameId, importance;
        switch (id) {
            case AlarmNotifications.ALARM_UPCOMING_NOTIFICATION_CHANNEL_ID:
                nameId = R.string.alarm_upcoming_notification;
                importance = NotificationManagerCompat.IMPORTANCE_LOW;
                break;
            case AlarmNotifications.ALARM_NOTIFICATION_CHANNEL_ID:
                nameId = R.string.alarm_notification;
                importance = NotificationManagerCompat.IMPORTANCE_HIGH;
                break;
            case AlarmNotifications.ALARM_MISSED_NOTIFICATION_CHANNEL_ID:
                nameId = R.string.alarm_missed_notification;
                importance = NotificationManagerCompat.IMPORTANCE_HIGH;
                break;
            case AlarmNotifications.ALARM_SNOOZE_NOTIFICATION_CHANNEL_ID:
                nameId = R.string.alarm_snooze_notification;
                importance = NotificationManagerCompat.IMPORTANCE_HIGH;
                break;

            case StopwatchNotificationBuilder.STOPWATCH_NOTIF_CHANNEL_ID:
                nameId = R.string.stopwatch_notification;
                importance = NotificationManagerCompat.IMPORTANCE_LOW;
                break;

            case TimerNotificationBuilder.TIMER_MODEL_NOTIFICATION_CHANNEL_ID:
                nameId = R.string.timer_model_notification;
                importance = NotificationManagerCompat.IMPORTANCE_HIGH;
                break;

            default:
                Log.e(TAG, "Invalid channel requested: " + id);
                return;
        }

        if (nameId != 0) {
            NotificationChannel channel = new NotificationChannel(
                    id, context.getString(nameId), importance);
            nm.createNotificationChannel(channel);
        }
    }

    private static void deleteChannel(NotificationManagerCompat nm, String channelId) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = nm.getNotificationChannel(channelId);
        if (channel != null) {
            nm.deleteNotificationChannel(channelId);
        }
    }

    private static void updateChannel(Context context, NotificationManagerCompat nm, String id) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = nm.getNotificationChannel(id);
        if (channel != null) {
            // Calling create on an existing id updates the details
            createChannel(context, nm, id);
        }
    }

    private static Set<String> getAllExistingChannelIds(NotificationManagerCompat nm) {
        Set<String> result = new ArraySet<>();
        for (NotificationChannel channel : nm.getNotificationChannels()) {
            result.add(channel.getId());
        }
        return result;
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

        // We recreate all existing channels so any language change or our name changes propagate
        // to the actual channels
        Set<String> existingChannelIds = getAllExistingChannelIds(nm);
        for (String id : existingChannelIds) {
            createChannel(context, nm, id);
        }
    }
}