/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;

import com.android.deskclock.alarms.AlarmActivity;
import com.android.deskclock.alarms.AlarmStateManager;
import com.android.deskclock.alarms.AlarmService;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.provider.AlarmInstance;
import com.android.deskclock.data.DataModel;
import com.android.deskclock.events.Events;

import java.util.Calendar;
import java.util.List;

public class AlarmInitReceiver extends BroadcastReceiver {

    /**
     * When running on N devices, we're interested in the boot completed event that is sent while
     * the user is still locked, so that we can schedule alarms.
     */
    @SuppressLint("InlinedApi")
    private static final String ACTION_BOOT_COMPLETED = Utils.isNOrLater()
            ? Intent.ACTION_LOCKED_BOOT_COMPLETED : Intent.ACTION_BOOT_COMPLETED;


    // Indicates that it is power off alarm boot
    private static final String ALARM_BOOT_PROP = "ro.alarm_boot";
    // Power off alarm was handled in encryption mode
    private static final String ALARM_HANDLED_PROP = "ro.alarm_handled";
    // Alarm instance which was handled in encryption mode
    private static final String ALARM_INSTANCE_PROP = "ro.alarm_instance";

    private static final String DECRYPT_PROP = "vold.decrypt";

    private static final String ENCRYPTING_STATE = "trigger_restart_min_framework";
    private static final String ENCRYPTED_STATE = "1";

    private static final String ALARM_GLOBAL_ID_EXTRA = "intent.extra.alarm.global.id";

    /**
     * This receiver handles a variety of actions:
     *
     * <ul>
     *     <li>Clean up backup data that was recently restored to this device on
     *     ACTION_COMPLETE_RESTORE.</li>
     *     <li>Reset timers and stopwatch on ACTION_BOOT_COMPLETED</li>
     *     <li>Fix alarm states on ACTION_BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED,
     *     and LOCALE_CHANGED</li>
     *     <li>Rebuild notifications on MY_PACKAGE_REPLACED</li>
     * </ul>
     */
    @Override
    public void onReceive(final Context context, Intent intent) {
        final String action = intent.getAction();
        LogUtils.i("AlarmInitReceiver " + action);

        final PendingResult result = goAsync();
        final WakeLock wl = AlarmAlertWakeLock.createPartialWakeLock(context);
        wl.acquire();

        boolean isAlarmBoot = AlarmStateManager.isAlarmBoot();
        ContentResolver cr = context.getContentResolver();

        // We need to increment the global id out of the async task to prevent race conditions
        AlarmStateManager.updateGlobalIntentId(context);

        // Clear stopwatch data and reset timers because they rely on elapsed real-time values
        // which are meaningless after a device reboot.
        if (ACTION_BOOT_COMPLETED.equals(action)) {
            DataModel.getDataModel().clearLaps();
            DataModel.getDataModel().resetStopwatch();
            Events.sendStopwatchEvent(R.string.action_reset, R.string.label_reboot);
            DataModel.getDataModel().resetTimers(R.string.label_reboot);

            // When ALARM_HANDLED_PROP is true which means that the alarm is handled in encryption
            // mode. Find the handled alarm by alarm time and set it as dismiss state.
            if (!isAlarmBoot && SystemProperties.getBoolean(ALARM_HANDLED_PROP, false)) {
                long instanceTime = SystemProperties.getLong(ALARM_INSTANCE_PROP, 0);
                if (instanceTime != 0) {
                    List<AlarmInstance> alarmInstances = AlarmInstance
                            .getInstances(cr, null);
                    AlarmInstance alarmInstance = null;
                    for (AlarmInstance instance : alarmInstances) {
                        if (instance.getAlarmTime().getTimeInMillis() == instanceTime) {
                            alarmInstance = instance;
                            break;
                        }
                    }

                    if (alarmInstance != null) {
                        AlarmStateManager.setDismissState(context, alarmInstance);
                    }
                }
            }
        }

        // When ACTION_BOOT_COMPLETED comes, AlarmActivity should be started for
        // power off alarm.
        //     1. Normal mode: just get next firing alarm and pass it to alarm activity
        //     2. Encryption mode: We need to create an alarm as there is no firing alarm
        //        in this mode.
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) && isAlarmBoot) {
            AlarmInstance instance = AlarmInstance.getFirstAlarmInstance(cr);
            String cryptState = SystemProperties.get(DECRYPT_PROP);
            if (instance == null && (ENCRYPTING_STATE.equals(cryptState) ||
                    ENCRYPTED_STATE.equals(cryptState))) {
                Calendar c = Calendar.getInstance();
                Alarm a = new Alarm();
                a.hour = c.get(Calendar.HOUR_OF_DAY);
                a.minutes = c.get(Calendar.MINUTE);
                a.enabled = true;
                Alarm newAlarm = Alarm.addAlarm(cr, a);
                instance = newAlarm.createInstanceAfter(Calendar.getInstance());
                instance = AlarmInstance.addInstance(cr, instance);
            }

            if (instance != null) {
                AlarmStateManager.setFiredState(context, instance);
                AlarmService.startAlarm(context, instance);
            }
        }

        // Notifications are canceled by the system on application upgrade. This broadcast signals
        // that the new app is free to rebuild the notifications using the existing data.
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            DataModel.getDataModel().updateAllNotifications();
        }

        AsyncHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    // Process restored data if any exists
                    if (!DeskClockBackupAgent.processRestoredData(context)) {
                        // Update all the alarm instances on time change event
                        AlarmStateManager.fixAlarmInstances(context);
                    }
                } finally {
                    result.finish();
                    wl.release();
                    LogUtils.v("AlarmInitReceiver finished");
                }
            }
        });
    }
}
