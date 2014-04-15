/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.deskclock.alarms;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.widget.Toast;
import android.telephony.TelephonyManager;

import com.android.deskclock.AlarmAlertWakeLock;
import com.android.deskclock.AlarmClockFragment;
import com.android.deskclock.AlarmUtils;
import com.android.deskclock.AsyncHandler;
import com.android.deskclock.DeskClock;
import com.android.deskclock.Log;
import com.android.deskclock.R;
import com.android.deskclock.SettingsActivity;
import com.android.deskclock.Utils;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.provider.AlarmInstance;

import java.util.Calendar;
import java.util.List;

/**
 * This class handles all the state changes for alarm instances. You need to
 * register all alarm instances with the state manager if you want them to
 * be activated. If a major time change has occurred (ie. TIMEZONE_CHANGE, TIMESET_CHANGE),
 * then you must also re-register instances to fix their states.
 *
 * Please see {@link #registerInstance) for special transitions when major time changes
 * occur.
 *
 * Following states:
 *
 * SILENT_STATE:
 * This state is used when the alarm is activated, but doesn't need to display anything. It
 * is in charge of changing the alarm instance state to a LOW_NOTIFICATION_STATE.
 *
 * LOW_NOTIFICATION_STATE:
 * This state is used to notify the user that the alarm will go off
 * {@link AlarmInstance.LOW_NOTIFICATION_HOUR_OFFSET}. This
 * state handles the state changes to HIGH_NOTIFICATION_STATE, HIDE_NOTIFICATION_STATE and
 * DISMISS_STATE.
 *
 * HIDE_NOTIFICATION_STATE:
 * This is a transient state of the LOW_NOTIFICATION_STATE, where the user wants to hide the
 * notification. This will sit and wait until the HIGH_PRIORITY_NOTIFICATION should go off.
 *
 * HIGH_NOTIFICATION_STATE:
 * This state behaves like the LOW_NOTIFICATION_STATE, but doesn't allow the user to hide it.
 * This state is in charge of triggering a FIRED_STATE or DISMISS_STATE.
 *
 * SNOOZED_STATE:
 * The SNOOZED_STATE behaves like a HIGH_NOTIFICATION_STATE, but with a different message. It
 * also increments the alarm time in the instance to reflect the new snooze time.
 *
 * FIRED_STATE:
 * The FIRED_STATE is used when the alarm is firing. It will start the AlarmService, and wait
 * until the user interacts with the alarm via SNOOZED_STATE or DISMISS_STATE change. If the user
 * doesn't then it might be change to MISSED_STATE if auto-silenced was enabled.
 *
 * MISSED_STATE:
 * The MISSED_STATE is used when the alarm already fired, but the user could not interact with
 * it. At this point the alarm instance is dead and we check the parent alarm to see if we need
 * to disable or schedule a new alarm_instance. There is also a notification shown to the user
 * that he/she missed the alarm and that stays for
 * {@link AlarmInstance.MISSED_TIME_TO_LIVE_HOUR_OFFSET} or until the user acknownledges it.
 *
 * DISMISS_STATE:
 * This is really a transient state that will properly delete the alarm instance. Use this state,
 * whenever you want to get rid of the alarm instance. This state will also check the alarm
 * parent to see if it should disable or schedule a new alarm instance.
 */
public final class AlarmStateManager extends BroadcastReceiver {
    // These defaults must match the values in res/xml/settings.xml
    private static final String DEFAULT_SNOOZE_MINUTES = "10";

    // Intent action to trigger an instance state change.
    public static final String CHANGE_STATE_ACTION = "change_state";

    // Intent action to show the alarm and dismiss the instance
    public static final String SHOW_AND_DISMISS_ALARM_ACTION = "show_and_dismiss_alarm";

    // Extra key to set the desired state change.
    public static final String ALARM_STATE_EXTRA = "intent.extra.alarm.state";

    // Extra key to set the global broadcast id.
    private static final String ALARM_GLOBAL_ID_EXTRA = "intent.extra.alarm.global.id";

    // Intent category tag used when schedule state change intents in alarm manager.
    public static final String ALARM_MANAGER_TAG = "ALARM_MANAGER";

    // Buffer time in seconds to fire alarm instead of marking it missed.
    public static final int ALARM_FIRE_BUFFER = 15;

    public static int getGlobalIntentId(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(ALARM_GLOBAL_ID_EXTRA, -1);
    }

    public static void updateGloablIntentId(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int globalId = prefs.getInt(ALARM_GLOBAL_ID_EXTRA, -1) + 1;
        prefs.edit().putInt(ALARM_GLOBAL_ID_EXTRA, globalId).commit();
    }

    /**
     * Find and notify system what the next alarm that will fire. This is used
     * to update text in the system and widgets.
     *
     * @param context application context
     */
    public static void updateNextAlarm(Context context) {
        AlarmInstance nextAlarm = null;
        ContentResolver cr = context.getContentResolver();
        String activeAlarmQuery = AlarmInstance.ALARM_STATE + "<" + AlarmInstance.FIRED_STATE;
        for (AlarmInstance instance : AlarmInstance.getInstances(cr, activeAlarmQuery)) {
            if (nextAlarm == null || instance.getAlarmTime().before(nextAlarm.getAlarmTime())) {
                nextAlarm = instance;
            }
        }
        AlarmNotifications.broadcastNextAlarm(context, nextAlarm);
    }

    /**
     * Used by dismissed and missed states, to update parent alarm. This will either
     * disable, delete or reschedule parent alarm.
     *
     * @param context application context
     * @param instance to update parent for
     */
    private static void updateParentAlarm(Context context, AlarmInstance instance) {
        ContentResolver cr = context.getContentResolver();
        Alarm alarm = Alarm.getAlarm(cr, instance.mAlarmId);
        if (alarm == null) {
            Log.e("Parent has been deleted with instance: " + instance.toString());
            return;
        }

        if (!alarm.daysOfWeek.isRepeating()) {
            if (alarm.deleteAfterUse) {
                Log.i("Deleting parent alarm: " + alarm.id);
                Alarm.deleteAlarm(cr, alarm.id);
            } else {
                Log.i("Disabling parent alarm: " + alarm.id);
                alarm.enabled = false;
                Alarm.updateAlarm(cr, alarm);
            }
        } else {
            // This is a optimization for really old alarm instances. This prevent us
            // from scheduling and dismissing alarms up to current time.
            Calendar currentTime = Calendar.getInstance();
            Calendar alarmTime = instance.getAlarmTime();
            if (currentTime.after(alarmTime)) {
                alarmTime = currentTime;
            }
            AlarmInstance nextRepeatedInstance = alarm.createInstanceAfter(alarmTime);
            Log.i("Creating new instance for repeating alarm " + alarm.id + " at "  +
                    AlarmUtils.getFormattedTime(context, nextRepeatedInstance.getAlarmTime()));
            AlarmInstance.addInstance(cr, nextRepeatedInstance);
            registerInstance(context, nextRepeatedInstance, true);
        }
    }

    /**
     * Utility method to create a proper change state intent.
     *
     * @param context application context
     * @param tag used to make intent differ from other state change intents.
     * @param instance to change state to
     * @param state to change to.
     * @return intent that can be used to change an alarm instance state
     */
    public static Intent createStateChangeIntent(Context context, String tag,
            AlarmInstance instance, Integer state) {
        Intent intent = AlarmInstance.createIntent(context, AlarmStateManager.class, instance.mId);
        intent.setAction(CHANGE_STATE_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.addCategory(tag);
        intent.putExtra(ALARM_GLOBAL_ID_EXTRA, getGlobalIntentId(context));
        if (state != null) {
            intent.putExtra(ALARM_STATE_EXTRA, state.intValue());
        }
        return intent;
    }

    /**
     * Schedule alarm instance state changes with {@link AlarmManager}.
     *
     * @param context application context
     * @param time to trigger state change
     * @param instance to change state to
     * @param newState to change to
     */
    private static void scheduleInstanceStateChange(Context context, Calendar time,
            AlarmInstance instance, int newState) {
        long timeInMillis = time.getTimeInMillis();
        Log.v("Scheduling state change " + newState + " to instance " + instance.mId +
                " at " + AlarmUtils.getFormattedTime(context, time) + " (" + timeInMillis + ")");
        Intent stateChangeIntent = createStateChangeIntent(context, ALARM_MANAGER_TAG, instance,
                newState);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, instance.hashCode(),
                stateChangeIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent stateChangePowerOffIntent = createStateChangeIntent(context, ALARM_MANAGER_TAG,
                instance, -1);
        PendingIntent pendingPowerOffIntent = PendingIntent.getBroadcast(context,
                instance.hashCode(), stateChangePowerOffIntent, PendingIntent.FLAG_ONE_SHOT);
        if (Utils.isKitKatOrLater()) {
            am.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);

            am.setExact(AlarmManager.RTC_POWEROFF_WAKEUP, instance.getAlarmTime().getTimeInMillis(),
                    pendingPowerOffIntent);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
            am.set(AlarmManager.RTC_POWEROFF_WAKEUP, instance.getAlarmTime().getTimeInMillis(),
                    pendingPowerOffIntent);
        }
    }

    /**
     * Cancel all {@link AlarmManager} timers for instance.
     *
     * @param context application context
     * @param instance to disable all {@link AlarmManager} timers
     */
    private static void cancelScheduledInstance(Context context, AlarmInstance instance) {
        Log.v("Canceling instance " + instance.mId + " timers");

        // Create a PendingIntent that will match any one set for this instance
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, instance.hashCode(),
                createStateChangeIntent(context, ALARM_MANAGER_TAG, instance, null),
                PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent pendingPowerOffIntent = PendingIntent.getBroadcast(context,
                instance.hashCode(),
                createStateChangeIntent(context, ALARM_MANAGER_TAG, instance, null),
                PendingIntent.FLAG_ONE_SHOT);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        am.cancel(pendingPowerOffIntent);
        am.cancel(pendingIntent);
    }


    /**
     * This will set the alarm instance to the SILENT_STATE and update
     * the application notifications and schedule any state changes that need
     * to occur in the future.
     *
     * @param context application context
     * @param instance to set state to
     */
    public static void setSilentState(Context context, AlarmInstance instance) {
        Log.v("Setting silent state to instance " + instance.mId);

        // Update alarm in db
        ContentResolver contentResolver = context.getContentResolver();
        instance.mAlarmState = AlarmInstance.SILENT_STATE;
        AlarmInstance.updateInstance(contentResolver, instance);

        // Setup instance notification and scheduling timers
        AlarmNotifications.clearNotification(context, instance);
        scheduleInstanceStateChange(context, instance.getLowNotificationTime(),
                instance, AlarmInstance.LOW_NOTIFICATION_STATE);
    }

    /**
     * This will set the alarm instance to the LOW_NOTIFICATION_STATE and update
     * the application notifications and schedule any state changes that need
     * to occur in the future.
     *
     * @param context application context
     * @param instance to set state to
     */
    public static void setLowNotificationState(Context context, AlarmInstance instance) {
        Log.v("Setting low notification state to instance " + instance.mId);

        // Update alarm state in db
        ContentResolver contentResolver = context.getContentResolver();
        instance.mAlarmState = AlarmInstance.LOW_NOTIFICATION_STATE;
        AlarmInstance.updateInstance(contentResolver, instance);

        // Setup instance notification and scheduling timers
        AlarmNotifications.showLowPriorityNotification(context, instance);
        scheduleInstanceStateChange(context, instance.getHighNotificationTime(),
                instance, AlarmInstance.HIGH_NOTIFICATION_STATE);
    }

    /**
     * This will set the alarm instance to the HIDE_NOTIFICATION_STATE and update
     * the application notifications and schedule any state changes that need
     * to occur in the future.
     *
     * @param context application context
     * @param instance to set state to
     */
    public static void setHideNotificationState(Context context, AlarmInstance instance) {
        Log.v("Setting hide notification state to instance " + instance.mId);

        // Update alarm state in db
        ContentResolver contentResolver = context.getContentResolver();
        instance.mAlarmState = AlarmInstance.HIDE_NOTIFICATION_STATE;
        AlarmInstance.updateInstance(contentResolver, instance);

        // Setup instance notification and scheduling timers
        AlarmNotifications.clearNotification(context, instance);
        scheduleInstanceStateChange(context, instance.getHighNotificationTime(),
                instance, AlarmInstance.HIGH_NOTIFICATION_STATE);
    }

    /**
     * This will set the alarm instance to the HIGH_NOTIFICATION_STATE and update
     * the application notifications and schedule any state changes that need
     * to occur in the future.
     *
     * @param context application context
     * @param instance to set state to
     */
    public static void setHighNotificationState(Context context, AlarmInstance instance) {
        Log.v("Setting high notification state to instance " + instance.mId);

        // Update alarm state in db
        ContentResolver contentResolver = context.getContentResolver();
        instance.mAlarmState = AlarmInstance.HIGH_NOTIFICATION_STATE;
        AlarmInstance.updateInstance(contentResolver, instance);

        // Setup instance notification and scheduling timers
        AlarmNotifications.showHighPriorityNotification(context, instance);
        scheduleInstanceStateChange(context, instance.getAlarmTime(),
                instance, AlarmInstance.FIRED_STATE);
    }

    /**
     * This will set the alarm instance to the FIRED_STATE and update
     * the application notifications and schedule any state changes that need
     * to occur in the future.
     *
     * @param context application context
     * @param instance to set state to
     */
    public static void setFiredState(Context context, AlarmInstance instance) {
        Log.v("Setting fire state to instance " + instance.mId);

        // Update alarm state in db
        ContentResolver contentResolver = context.getContentResolver();
        instance.mAlarmState = AlarmInstance.FIRED_STATE;
        AlarmInstance.updateInstance(contentResolver, instance);

        // Start the alarm and schedule timeout timer for it
        AlarmService.startAlarm(context, instance);

        Calendar timeout = instance.getTimeout(context);
        if (timeout != null) {
            scheduleInstanceStateChange(context, timeout, instance, AlarmInstance.MISSED_STATE);
        }

        // Instance not valid anymore, so find next alarm that will fire and notify system
        updateNextAlarm(context);
    }

    /**
     * This will set the alarm instance to the SNOOZE_STATE and update
     * the application notifications and schedule any state changes that need
     * to occur in the future.
     *
     * @param context application context
     * @param instance to set state to
     */
    public static void setSnoozeState(Context context, AlarmInstance instance) {
        // Stop alarm if this instance is firing it
        AlarmService.stopAlarm(context, instance);

        // Calculate the new snooze alarm time
        String snoozeMinutesStr = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(SettingsActivity.KEY_ALARM_SNOOZE, DEFAULT_SNOOZE_MINUTES);
        int snoozeMinutes = Integer.parseInt(snoozeMinutesStr);
        Calendar newAlarmTime = Calendar.getInstance();
        newAlarmTime.add(Calendar.MINUTE, snoozeMinutes);

        // Update alarm state and new alarm time in db.
        Log.v("Setting snoozed state to instance " + instance.mId + " for "
                + AlarmUtils.getFormattedTime(context, newAlarmTime));
        instance.setAlarmTime(newAlarmTime);
        instance.mAlarmState = AlarmInstance.SNOOZE_STATE;
        AlarmInstance.updateInstance(context.getContentResolver(), instance);

        // Setup instance notification and scheduling timers
        AlarmNotifications.showSnoozeNotification(context, instance);
        scheduleInstanceStateChange(context, instance.getAlarmTime(),
                instance, AlarmInstance.FIRED_STATE);

        // Display the snooze minutes in a toast.
        String displayTime = String.format(context.getResources().getQuantityText
                (R.plurals.alarm_alert_snooze_set, snoozeMinutes).toString(), snoozeMinutes);
        Toast.makeText(context, displayTime, Toast.LENGTH_LONG).show();

        // Instance time changed, so find next alarm that will fire and notify system
        updateNextAlarm(context);

    }

    /**
     * This will set the alarm instance to the one minute late and update
     * the application notifications and schedule any state changes that need
     * to occur in the future.
     *
     * @param context application context
     * @param instance to set state to
     */
    public static void setOneminutelate(Context context, AlarmInstance instance) {
        AlarmService.stopAlarm(context, instance);
        int snoozeMinutes = 1;

        // Set alarm time to next minute.Update alarm state and new alarm time in db.
        Calendar newAlarmTime = Calendar.getInstance();
        newAlarmTime.add(Calendar.MINUTE, snoozeMinutes);
        instance.setAlarmTime(newAlarmTime);
        instance.mAlarmState = AlarmInstance.SNOOZE_STATE;
        AlarmInstance.updateInstance(context.getContentResolver(), instance);
        scheduleInstanceStateChange(context, instance.getAlarmTime(),
                instance, AlarmInstance.FIRED_STATE);

        // Instance time changed, so find next alarm that will fire and notify system
        updateNextAlarm(context);
    }

    /**
     * This will set the alarm instance to the MISSED_STATE and update
     * the application notifications and schedule any state changes that need
     * to occur in the future.
     *
     * @param context application context
     * @param instance to set state to
     */
    public static void setMissedState(Context context, AlarmInstance instance) {
        Log.v("Setting missed state to instance " + instance.mId);
        // Stop alarm if this instance is firing it
        AlarmService.stopAlarm(context, instance);

        // Check parent if it needs to reschedule, disable or delete itself
        if (instance.mAlarmId != null) {
            updateParentAlarm(context, instance);
        }

        // Update alarm state
        ContentResolver contentResolver = context.getContentResolver();
        instance.mAlarmState = AlarmInstance.MISSED_STATE;
        AlarmInstance.updateInstance(contentResolver, instance);

        // Setup instance notification and scheduling timers
        AlarmNotifications.showMissedNotification(context, instance);
        scheduleInstanceStateChange(context, instance.getMissedTimeToLive(),
                instance, AlarmInstance.DISMISSED_STATE);

        // Instance is not valid anymore, so find next alarm that will fire and notify system
        updateNextAlarm(context);

    }

    /**
     * This will set the alarm instance to the SILENT_STATE and update
     * the application notifications and schedule any state changes that need
     * to occur in the future.
     *
     * @param context application context
     * @param instance to set state to
     */
    public static void setDismissState(Context context, AlarmInstance instance) {
        Log.v("Setting dismissed state to instance " + instance.mId);

        // Remove all other timers and notifications associated to it
        unregisterInstance(context, instance);

        // Check parent if it needs to reschedule, disable or delete itself
        if (instance.mAlarmId != null) {
            updateParentAlarm(context, instance);
        }

        // Delete instance as it is not needed anymore
        AlarmInstance.deleteInstance(context.getContentResolver(), instance.mId);

        // Instance is not valid anymore, so find next alarm that will fire and notify system
        updateNextAlarm(context);
    }

    /**
     * This will not change the state of instance, but remove it's notifications and
     * alarm timers.
     *
     * @param context application context
     * @param instance to unregister
     */
    public static void unregisterInstance(Context context, AlarmInstance instance) {
        // Stop alarm if this instance is firing it
        AlarmService.stopAlarm(context, instance);
        AlarmNotifications.clearNotification(context, instance);
        cancelScheduledInstance(context, instance);
    }

    /**
     * This registers the AlarmInstance to the state manager. This will look at the instance
     * and choose the most appropriate state to put it in. This is primarily used by new
     * alarms, but it can also be called when the system time changes.
     *
     * Most state changes are handled by the states themselves, but during major time changes we
     * have to correct the alarm instance state. This means we have to handle special cases as
     * describe below:
     *
     * <ul>
     *     <li>Make sure all dismissed alarms are never re-activated</li>
     *     <li>Make sure firing alarms stayed fired unless they should be auto-silenced</li>
     *     <li>Missed instance that have parents should be re-enabled if we went back in time</li>
     *     <li>If alarm was SNOOZED, then show the notification but don't update time</li>
     *     <li>If low priority notification was hidden, then make sure it stays hidden</li>
     * </ul>
     *
     * If none of these special case are found, then we just check the time and see what is the
     * proper state for the instance.
     *
     * @param context application context
     * @param instance to register
     */
    public static void registerInstance(Context context, AlarmInstance instance,
            boolean updateNextAlarm) {
        Calendar currentTime = Calendar.getInstance();
        Calendar alarmTime = instance.getAlarmTime();
        Calendar timeoutTime = instance.getTimeout(context);
        Calendar lowNotificationTime = instance.getLowNotificationTime();
        Calendar highNotificationTime = instance.getHighNotificationTime();
        Calendar missedTTL = instance.getMissedTimeToLive();

        // Handle special use cases here
        if (instance.mAlarmState == AlarmInstance.DISMISSED_STATE) {
            // This should never happen, but add a quick check here
            Log.e("Alarm Instance is dismissed, but never deleted");
            setDismissState(context, instance);
            return;
        } else if (instance.mAlarmState == AlarmInstance.FIRED_STATE) {
            // Keep alarm firing, unless it should be timed out
            boolean hasTimeout = timeoutTime != null && currentTime.after(timeoutTime);
            if (!hasTimeout) {
                setFiredState(context, instance);
                return;
            }
        } else if (instance.mAlarmState == AlarmInstance.MISSED_STATE) {
            if (currentTime.before(alarmTime)) {
                if (instance.mAlarmId == null) {
                    // This instance parent got deleted (ie. deleteAfterUse), so
                    // we should not re-activate it.-
                    setDismissState(context, instance);
                    return;
                }

                // TODO: This will re-activate missed snoozed alarms, but will
                // use our normal notifications. This is not ideal, but very rare use-case.
                // We should look into fixing this in the future.

                // Make sure we re-enable the parent alarm of the instance
                // because it will get activated by by the below code
                ContentResolver cr = context.getContentResolver();
                Alarm alarm = Alarm.getAlarm(cr, instance.mAlarmId);
                alarm.enabled = true;
                Alarm.updateAlarm(cr, alarm);
            }
        }

        // Fix states that are time sensitive
        if (currentTime.after(missedTTL)) {
            // Alarm is so old, just dismiss it
            setDismissState(context, instance);
        } else if (currentTime.after(alarmTime)) {
            // There is a chance that the TIME_SET occurred right when the alarm should go off, so
            // we need to add a check to see if we should fire the alarm instead of marking it
            // missed.
            Calendar alarmBuffer = Calendar.getInstance();
            alarmBuffer.setTime(alarmTime.getTime());
            alarmBuffer.add(Calendar.SECOND, ALARM_FIRE_BUFFER);
            if (currentTime.before(alarmBuffer)) {
                setFiredState(context, instance);
            } else {
                setMissedState(context, instance);
            }
        } else if (instance.mAlarmState == AlarmInstance.SNOOZE_STATE) {
            // We only want to display snooze notification and not update the time,
            // so handle showing the notification directly
            AlarmNotifications.showSnoozeNotification(context, instance);
            scheduleInstanceStateChange(context, instance.getAlarmTime(),
                    instance, AlarmInstance.FIRED_STATE);
        } else if (currentTime.after(highNotificationTime)) {
            setHighNotificationState(context, instance);
        } else if (currentTime.after(lowNotificationTime)) {
            // Only show low notification if it wasn't hidden in the past
            if (instance.mAlarmState == AlarmInstance.HIDE_NOTIFICATION_STATE) {
                setHideNotificationState(context, instance);
            } else {
                setLowNotificationState(context, instance);
            }
        } else {
          // Alarm is still active, so initialize as a silent alarm
          setSilentState(context, instance);
        }

        // The caller prefers to handle updateNextAlarm for optimization
        if (updateNextAlarm) {
            updateNextAlarm(context);
        }
    }

    /**
     * This will delete and unregister all instances associated with alarmId, without affect
     * the alarm itself. This should be used whenever modifying or deleting an alarm.
     *
     * @param context application context
     * @param alarmId to find instances to delete.
     */
    public static void deleteAllInstances(Context context, long alarmId) {
        ContentResolver cr = context.getContentResolver();
        List<AlarmInstance> instances = AlarmInstance.getInstancesByAlarmId(cr, alarmId);
        for (AlarmInstance instance : instances) {
            unregisterInstance(context, instance);
            AlarmInstance.deleteInstance(context.getContentResolver(), instance.mId);
        }
        updateNextAlarm(context);
    }

    /**
     * Fix and update all alarm instance when a time change event occurs.
     *
     * @param context application context
     */
    public static void fixAlarmInstances(Context context) {
        // Register all instances after major time changes or when phone restarts
        // TODO: Refactor this code to not use the overloaded registerInstance method.
        ContentResolver contentResolver = context.getContentResolver();
        for (AlarmInstance instance : AlarmInstance.getInstances(contentResolver, null)) {
            AlarmStateManager.registerInstance(context, instance, false);
        }
        AlarmStateManager.updateNextAlarm(context);
    }

    /**
     * Utility method to set alarm instance state via constants.
     *
     * @param context application context
     * @param instance to change state on
     * @param state to change to
     */
    public void setAlarmState(Context context, AlarmInstance instance, int state) {
        switch(state) {
            case AlarmInstance.SILENT_STATE:
                setSilentState(context, instance);
                break;
            case AlarmInstance.LOW_NOTIFICATION_STATE:
                setLowNotificationState(context, instance);
                break;
            case AlarmInstance.HIDE_NOTIFICATION_STATE:
                setHideNotificationState(context, instance);
                break;
            case AlarmInstance.HIGH_NOTIFICATION_STATE:
                setHighNotificationState(context, instance);
                break;
            case AlarmInstance.FIRED_STATE:
                setFiredState(context, instance);
                break;
            case AlarmInstance.SNOOZE_STATE:
                setSnoozeState(context, instance);
                break;
            case AlarmInstance.MISSED_STATE:
                setMissedState(context, instance);
                break;
            case AlarmInstance.DISMISSED_STATE:
                setDismissState(context, instance);
                break;
            default:
                Log.e("Trying to change to unknown alarm state: " + state);
        }
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final PendingResult result = goAsync();
        final PowerManager.WakeLock wl = AlarmAlertWakeLock.createPartialWakeLock(context);
        wl.acquire();
        AsyncHandler.post(new Runnable() {
            @Override
            public void run() {
                handleIntent(context, intent);
                result.finish();
                wl.release();
            }
        });
    }

    private void handleIntent(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.v("AlarmStateManager received intent " + intent);
        if (CHANGE_STATE_ACTION.equals(action)) {
            Uri uri = intent.getData();
            AlarmInstance instance = AlarmInstance.getInstance(context.getContentResolver(),
                    AlarmInstance.getId(uri));
            if (instance == null) {
                // Not a big deal, but it shouldn't happen
                Log.e("Can not change state for unknown instance: " + uri);
                return;
            }

            int globalId = getGlobalIntentId(context);
            int intentId = intent.getIntExtra(ALARM_GLOBAL_ID_EXTRA, -1);
            int alarmState = intent.getIntExtra(ALARM_STATE_EXTRA, -1);
            if (intentId != globalId) {
                Log.i("Ignoring old Intent. IntentId: " + intentId + " GlobalId: " + globalId +
                        " AlarmState: " + alarmState);
                return;
            }

            // If the phone is busy, keep the alarm snoozing.When the call is ended,
            // the new coming alarm or the alarm which wakes from sooze,will skip the codes here
            // and continue show the alarm as normal.
            if (context.getResources().getBoolean(R.bool.config_delayalarm)) {
                TelephonyManager mTelephonyManager = (TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE);
                if ((mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
                        && (alarmState == AlarmInstance.FIRED_STATE)) {
                    snooze(context, intent, instance);
                    return;
                }
            }

            if (alarmState >= 0) {
                setAlarmState(context, instance, alarmState);
            } else {
                // No need to register instance again when alarmState
                // equals POWER_OFF_ALARM_STATE. POWER_OFF_ALARM_STATE
                // is an invalid state for rtc power off alarm.
                if (alarmState == AlarmInstance.POWER_OFF_ALARM_STATE)
                {
                    return;
                }
                registerInstance(context, instance, true);
            }
        } else if (SHOW_AND_DISMISS_ALARM_ACTION.equals(action)) {
            Uri uri = intent.getData();
            AlarmInstance instance = AlarmInstance.getInstance(context.getContentResolver(),
                    AlarmInstance.getId(uri));

            long alarmId = instance.mAlarmId == null ? Alarm.INVALID_ID : instance.mAlarmId;
            Intent viewAlarmIntent = Alarm.createIntent(context, DeskClock.class, alarmId);
            viewAlarmIntent.putExtra(DeskClock.SELECT_TAB_INTENT_EXTRA, DeskClock.ALARM_TAB_INDEX);
            viewAlarmIntent.putExtra(AlarmClockFragment.SCROLL_TO_ALARM_INTENT_EXTRA, alarmId);
            viewAlarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(viewAlarmIntent);
            setDismissState(context, instance);
        }
    }

    /**
     * Make the alarm snooze for one second.If the phone is still busy in call,
     * this method will be called again to snooze for another one second.
     * If the phone is not busy in call anymore, this method will not be
     * called, and the alarm will wake up within one second.
     */
    private void snooze(Context context, Intent intent, AlarmInstance instance) {
        Calendar currentTime = Calendar.getInstance();
        if ((currentTime.get(Calendar.SECOND) < 57)
                && (currentTime.get(Calendar.MINUTE) == instance.mMinute)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {}
            Uri uri = intent.getData();
            AlarmInstance newInstance = AlarmInstance.getInstance(context.getContentResolver(),
                    AlarmInstance.getId(uri));
            if (newInstance == null) {
                // If AlarmInstance is turn to null,return.
                return;
            }

            // Notify the user that the alarm has been snoozed.
            Intent cancelSnooze = createStateChangeIntent(context, ALARM_MANAGER_TAG, newInstance,
                    AlarmInstance.DISMISSED_STATE);
            PendingIntent broadcast = PendingIntent.getBroadcast(context, instance.hashCode(),
                    cancelSnooze, 0);
            String label = newInstance.getLabelOrDefault(context);
            label = context.getString(R.string.alarm_notify_snooze_label, label);
            NotificationManager nm = (NotificationManager) context
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            Notification n = new Notification(R.drawable.stat_notify_alarm, label, 0);
            n.setLatestEventInfo(context, label,
                    context.getString(R.string.alarm_notify_snooze_text,
                            AlarmUtils.getFormattedTime(context, instance.getAlarmTime())),
                    broadcast);
            n.flags |= Notification.FLAG_AUTO_CANCEL | Notification.FLAG_ONGOING_EVENT;
            nm.notify(instance.hashCode(), n);

            scheduleInstanceStateChange(context, newInstance.getAlarmTime(), newInstance,
                    AlarmInstance.FIRED_STATE);
        } else {
            // when alarm is about to overtime,let it snooze to next minute
            setOneminutelate(context, instance);
        }
    }
}
