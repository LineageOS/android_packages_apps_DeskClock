/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.android.deskclock.R;

public class NotificationUtils {
    public static String STOPWATCH_CHANNEL = "stopwatch_notifications_channel";
    public static String TIMER_CHANNEL = "timer_notifications_channel";
    public static String ALARM_CHANNEL = "alarm_notifications_channel";

    public static void createChannel(Context context, String id, String name, int importance) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));

        NotificationChannel channel = new NotificationChannel(id, name, importance);

        manager.createNotificationChannel(channel);
    }

    public static void createStopwatchChannels(Context context) {
        createChannel(context, STOPWATCH_CHANNEL, context.getString(R.string.stopwatch_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
    }

    public static void createTimerChannels(Context context) {
        createChannel(context, TIMER_CHANNEL, context.getString(R.string.timer_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
    }

    public static void createAlarmChannels(Context context) {
        createChannel(context, ALARM_CHANNEL, context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
    }
}
