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
 * limitations under the License
 */

package com.android.deskclock;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringDef;
import android.support.v4.app.NotificationCompat;
import android.support.v4.os.BuildCompat;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Contains info on how to create {@link NotificationChannel
 * NotificationChannels}
 */
public class NotificationChannelManager {

    private static NotificationChannelManager sInstance;

    public static NotificationChannelManager getInstance() {
        if (sInstance == null) {
            sInstance = new NotificationChannelManager();
        }
        return sInstance;
    }

    /**
     * Set the channel of notification appropriately. Will create the channel if
     * it does not already exist. Safe to call pre-O (will no-op).
     */
    @TargetApi(26)
    public static void applyChannel(@NonNull NotificationCompat.Builder notification,
            @NonNull Context context, @Channel String channelId) {
        if (!BuildCompat.isAtLeastO()) {
            return;
        }

        NotificationChannel channel = NotificationChannelManager
                .getInstance().getChannel(context, channelId);
        notification.setChannelId(channel.getId());
    }

    /** The base Channel IDs for {@link NotificationChannel} */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({ Channel.EVENT_EXPIRED, Channel.HIGH_NOTIFICATION,
            Channel.DEFAULT_NOTIFICATION })
    public @interface Channel {
        String EVENT_EXPIRED = "eventExpire";
        String HIGH_NOTIFICATION = "highNotif";
        String DEFAULT_NOTIFICATION = "defaultNotif";
    }

    @Channel
    private static final String[] allChannels = new String[] {
            Channel.EVENT_EXPIRED,
            Channel.HIGH_NOTIFICATION,
            Channel.DEFAULT_NOTIFICATION
    };

    @NonNull
    @RequiresApi(26)
    private NotificationChannel getChannel(@NonNull Context context,
            @Channel String channelId) {
        NotificationChannel channel = getNotificationManager(context)
                .getNotificationChannel(channelId);
        if (channel == null) {
            channel = createChannel(context, channelId);
        }
        return channel;
    }

    @RequiresApi(26)
    private NotificationChannel createChannel(Context context,
            @Channel String channelId) {
        Uri silentRingtone = Uri.EMPTY;
        CharSequence name;
        int importance;
        boolean canShowBadge;
        boolean lights;
        boolean vibration;
        boolean dnd;
        Uri sound;

        switch (channelId) {
        case Channel.EVENT_EXPIRED:
            name = context.getString(R.string.channel_expired_events);
            importance = NotificationManager.IMPORTANCE_HIGH;
            canShowBadge = false;
            lights = false;
            vibration = false;
            sound = null;
            dnd = true;
            break;

        case Channel.HIGH_NOTIFICATION:
            name = context.getString(R.string.channel_important);
            importance = NotificationManager.IMPORTANCE_DEFAULT;
            canShowBadge = false;
            lights = false;
            vibration = false;
            sound = null;
            dnd = true;
            break;

        case Channel.DEFAULT_NOTIFICATION:
            name = context.getString(R.string.channel_default);
            importance = NotificationManager.IMPORTANCE_LOW;
            canShowBadge = false;
            lights = false;
            vibration = false;
            sound = null;
            dnd = true;
            break;

        default:
            throw new IllegalArgumentException("Unknown channel: " + channelId);
        }
        NotificationChannel channel = new NotificationChannel(channelId, name,
                importance);
        channel.setShowBadge(canShowBadge);
        channel.enableVibration(vibration);
        channel.setSound(sound, null);
        channel.enableLights(lights);
        channel.setBypassDnd(dnd);

        getNotificationManager(context).createNotificationChannel(channel);
        return channel;
    }

    private static NotificationManager getNotificationManager(
            @NonNull Context context) {
        return context.getSystemService(NotificationManager.class);
    }
}
