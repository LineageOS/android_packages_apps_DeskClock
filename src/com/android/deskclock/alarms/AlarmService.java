/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.android.deskclock.AlarmAlertWakeLock;
import com.android.deskclock.LogUtils;
import com.android.deskclock.R;
import com.android.deskclock.Utils;
import com.android.deskclock.events.Events;
import com.android.deskclock.provider.AlarmInstance;
import com.android.deskclock.settings.SettingsActivity;

import cyanogenmod.app.Profile;
import cyanogenmod.app.ProfileManager;

/**
 * This service is in charge of starting/stopping the alarm. It will bring up and manage the
 * {@link AlarmActivity} as well as {@link AlarmKlaxon}.
 *
 * Registers a broadcast receiver to listen for snooze/dismiss intents. The broadcast receiver
 * exits early if AlarmActivity is bound to prevent double-processing of the snooze/dismiss intents.
 */
public class AlarmService extends Service {
    /**
     * AlarmActivity and AlarmService (when unbound) listen for this broadcast intent
     * so that other applications can snooze the alarm (after ALARM_ALERT_ACTION and before
     * ALARM_DONE_ACTION).
     */
    public static final String ALARM_SNOOZE_ACTION = "com.android.deskclock.ALARM_SNOOZE";

    /**
     * AlarmActivity and AlarmService listen for this broadcast intent so that other
     * applications can dismiss the alarm (after ALARM_ALERT_ACTION and before ALARM_DONE_ACTION).
     */
    public static final String ALARM_DISMISS_ACTION = "com.android.deskclock.ALARM_DISMISS";

    /** A public action sent by AlarmService when the alarm has started. */
    public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";

    /** A public action sent by AlarmService when the alarm has stopped for any reason. */
    public static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";

    /** Private action used to stop an alarm with this service. */
    public static final String STOP_ALARM_ACTION = "STOP_ALARM";

    /** Binder given to AlarmActivity */
    private final IBinder mBinder = new Binder();

    /** Whether the service is currently bound to AlarmActivity */
    private boolean mIsBound = false;

    /** Whether the receiver is currently registered */
    private boolean mIsRegistered = false;

    private SensorManager mSensorManager;
    private int mFlipAction;
    private int mShakeAction;
    private static final int ALARM_NO_ACTION = 0;
    private static final int ALARM_SNOOZE = 1;
    private static final int ALARM_DISMISS = 2;

    @Override
    public IBinder onBind(Intent intent) {
        mIsBound = true;
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mIsBound = false;
        return super.onUnbind(intent);
    }

    /**
     * Utility method to help stop an alarm properly. Nothing will happen, if alarm is not firing
     * or using a different instance.
     *
     * @param context application context
     * @param instance you are trying to stop
     */
    public static void stopAlarm(Context context, AlarmInstance instance) {
        final Intent intent = AlarmInstance.createIntent(context, AlarmService.class, instance.mId)
                .setAction(STOP_ALARM_ACTION);

        // We don't need a wake lock here, since we are trying to kill an alarm
        context.startService(intent);
    }

    private TelephonyManager mTelephonyManager;
    private int mInitialCallState;
    private AlarmInstance mCurrentAlarm = null;

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String ignored) {
            // The user might already be in a call when the alarm fires. When
            // we register onCallStateChanged, we get the initial in-call state
            // which kills the alarm. Check against the initial call state so
            // we don't kill the alarm during a call.

            if (AlarmService.this.getResources().getBoolean(R.bool.config_silent_during_call)
                    && state != TelephonyManager.CALL_STATE_IDLE) {
                sendBroadcast(AlarmStateManager.createStateChangeIntent(AlarmService.this,
                        "AlarmService", mCurrentAlarm, AlarmInstance.MISSED_STATE));
                return;
            }

            if (state != TelephonyManager.CALL_STATE_IDLE && state != mInitialCallState) {
                sendBroadcast(AlarmStateManager.createStateChangeIntent(AlarmService.this,
                        "AlarmService", mCurrentAlarm, AlarmInstance.MISSED_STATE));
            }
        }
    };

    private void changeToProfile(final Context context, final AlarmInstance instance) {
        final ProfileManager profileManager = ProfileManager.getInstance(this);
        if (!profileManager.isProfilesEnabled()) {
            LogUtils.v("Profiles are disabled");
            return;
        }

        // The alarm is defined to change the active profile?
        if (instance.mProfile.equals(ProfileManager.NO_PROFILE)) {
            LogUtils.v("Alarm doesn't define a profile to change to");
            return;
        }

        // Ensure that the profile still exists
        Profile profile = profileManager.getProfile(instance.mProfile);
        if (profile == null) {
            LogUtils.e("The profile \"" + instance.mProfile
                    + "\" does not exist. Can't change to this profile");
            return;
        }

        // Is the current profile different?
        Profile activeProfile = profileManager.getActiveProfile();
        if (activeProfile == null || !profile.getUuid().equals(activeProfile.getUuid())) {
            // Change to profile
            LogUtils.i("Changing to profile \"" + profile.getName() + "\" (" + profile.getUuid()
                    + ") requested by alarm \"" + instance.mLabel + "\" (" + instance.mId + ")");
            profileManager.setActiveProfile(profile.getUuid());
        } else {
            LogUtils.v("The profile \"" + profile.getName() + "\" (" + profile.getUuid()
                    + " is already active. No need to change to");
        }
    }

    public static void startAlarm(Context context, AlarmInstance instance) {
        final Intent intent = AlarmInstance.createIntent(context, AlarmService.class, instance.mId)
                .setAction(AlarmStateManager.CHANGE_STATE_ACTION);
        intent.putExtra(AlarmStateManager.ALARM_STATE_EXTRA, AlarmInstance.FIRED_STATE);
        AlarmAlertWakeLock.acquireCpuWakeLock(context);
        context.startService(intent);
    }

    private void startAlarm(AlarmInstance instance) {
        LogUtils.v("AlarmService.start with instance: " + instance.mId);
        if (mCurrentAlarm != null) {
            AlarmStateManager.setMissedState(this, mCurrentAlarm);
            stopCurrentAlarm();
        }

        AlarmAlertWakeLock.acquireCpuWakeLock(this);

        Events.sendEvent(R.string.category_alarm, R.string.action_fire, 0);

        mCurrentAlarm = instance;
        if (AlarmStateManager.isAlarmBoot()) {
            Intent alarmIntent = AlarmInstance.createIntent(this, AlarmActivity.class,
                        instance.mId);
            alarmIntent.setAction("fullscreen_activity");
            alarmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            this.startActivity(alarmIntent);
        } else {
            AlarmNotifications.showAlarmNotification(this, mCurrentAlarm);
        }
        mInitialCallState = mTelephonyManager.getCallState();
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        AlarmKlaxon.start(this, mCurrentAlarm);
        changeToProfile(this, mCurrentAlarm);
        sendBroadcast(new Intent(ALARM_ALERT_ACTION));

        attachListeners();
    }

    private void stopCurrentAlarm() {
        if (mCurrentAlarm == null) {
            LogUtils.v("There is no current alarm to stop");
            return;
        }

        final long instanceId = mCurrentAlarm.mId;
        LogUtils.v("AlarmService.stop with instance: %s", instanceId);

        AlarmKlaxon.stop(this);
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        sendBroadcast(new Intent(ALARM_DONE_ACTION));

        // Since we use the same id for all notifications, the system has no way to distinguish the
        // firing notification we were bound to from other subsequent notifications posted for the
        // same AlarmInstance (e.g. after snoozing). We workaround the issue by forcing removal of
        // the notification and re-posting it.
        stopForeground(true /* removeNotification */);
        mCurrentAlarm = AlarmInstance.getInstance(getContentResolver(), instanceId);
        if (mCurrentAlarm != null) {
            AlarmNotifications.updateNotification(this, mCurrentAlarm);
        }

        detachListeners();

        mCurrentAlarm = null;
        AlarmAlertWakeLock.releaseCpuLock();
    }

    private final BroadcastReceiver mActionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            LogUtils.i("AlarmService received intent %s", action);
            if (mCurrentAlarm == null || mCurrentAlarm.mAlarmState != AlarmInstance.FIRED_STATE) {
                LogUtils.i("No valid firing alarm");
                return;
            }

            if (mIsBound) {
                LogUtils.i("AlarmActivity bound; AlarmService no-op");
                return;
            }

            switch (action) {
                case ALARM_SNOOZE_ACTION:
                    // Set the alarm state to snoozed.
                    // If this broadcast receiver is handling the snooze intent then AlarmActivity
                    // must not be showing, so always show snooze toast.
                    AlarmStateManager.setSnoozeState(context, mCurrentAlarm, true /* showToast */);
                    Events.sendAlarmEvent(R.string.action_snooze, R.string.label_intent);
                    break;
                case ALARM_DISMISS_ACTION:
                    // Set the alarm state to dismissed.
                    AlarmStateManager.deleteInstanceAndUpdateParent(context, mCurrentAlarm);
                    Events.sendAlarmEvent(R.string.action_dismiss, R.string.label_intent);
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        // Register the broadcast receiver
        final IntentFilter filter = new IntentFilter(ALARM_SNOOZE_ACTION);
        filter.addAction(ALARM_DISMISS_ACTION);
        registerReceiver(mActionsReceiver, filter);
        mIsRegistered = true;

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        SharedPreferences prefs = Utils.getDefaultSharedPreferences(this);
        String action = prefs.getString(SettingsActivity.KEY_FLIP_ACTION, null);
        mFlipAction = (action != null) ? Integer.parseInt(action) :
            getResources().getInteger(R.integer.config_defaultActionFlip);
        action  = prefs.getString(SettingsActivity.KEY_SHAKE_ACTION, null);
        mShakeAction = (action != null) ? Integer.parseInt(action) :
            getResources().getInteger(R.integer.config_defaultActionShake);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.v("AlarmService.onStartCommand() with %s", intent);

        final long instanceId = AlarmInstance.getId(intent.getData());
        switch (intent.getAction()) {
            case AlarmStateManager.CHANGE_STATE_ACTION:
                final ContentResolver cr = this.getContentResolver();
                final AlarmInstance instance = AlarmInstance.getInstance(cr, instanceId);
                if (instance == null) {
                    LogUtils.e("No instance found to start alarm: %d", instanceId);
                    if (mCurrentAlarm != null) {
                        // Only release lock if we are not firing alarm
                        AlarmAlertWakeLock.releaseCpuLock();
                    }
                    break;
                }

                if (mCurrentAlarm != null && mCurrentAlarm.mId == instanceId) {
                    LogUtils.e("Alarm already started for instance: %d", instanceId);
                    break;
                }
                startAlarm(instance);
                break;
            case STOP_ALARM_ACTION:
                if (mCurrentAlarm != null && mCurrentAlarm.mId != instanceId) {
                    LogUtils.e("Can't stop alarm for instance: %d because current alarm is: %d",
                            instanceId, mCurrentAlarm.mId);
                    break;
                }
                stopCurrentAlarm();
                stopSelfResult(startId);
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        LogUtils.v("AlarmService.onDestroy() called");
        super.onDestroy();
        if (mCurrentAlarm != null) {
            stopCurrentAlarm();
        }

        if (mIsRegistered) {
            unregisterReceiver(mActionsReceiver);
            mIsRegistered = false;
        }
    }

    private interface ResettableSensorEventListener extends SensorEventListener {
        public void reset();
    }

    private final ResettableSensorEventListener mFlipListener =
        new ResettableSensorEventListener() {
        // Accelerometers are not quite accurate.
        private static final float GRAVITY_UPPER_THRESHOLD = 1.3f * SensorManager.STANDARD_GRAVITY;
        private static final float GRAVITY_LOWER_THRESHOLD = 0.7f * SensorManager.STANDARD_GRAVITY;
        private static final int SENSOR_SAMPLES = 3;

        private boolean mStopped;
        private boolean mWasFaceUp;
        private boolean[] mSamples = new boolean[SENSOR_SAMPLES];
        private int mSampleIndex;

        @Override
        public void onAccuracyChanged(Sensor sensor, int acc) {
        }

        @Override
        public void reset() {
            mWasFaceUp = false;
            mStopped = false;
            for (int i = 0; i < SENSOR_SAMPLES; i++) {
                mSamples[i] = false;
            }
        }

        private boolean filterSamples() {
            boolean allPass = true;
            for (boolean sample : mSamples) {
                allPass = allPass && sample;
            }
            return allPass;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // Add a sample overwriting the oldest one. Several samples
            // are used to avoid the erroneous values the sensor sometimes
            // returns.
            float z = event.values[2];

            if (mStopped) {
                return;
            }

            if (!mWasFaceUp) {
                // Check if its face up enough.
                mSamples[mSampleIndex] = (z > GRAVITY_LOWER_THRESHOLD) &&
                    (z < GRAVITY_UPPER_THRESHOLD);

                // face up
                if (filterSamples()) {
                    mWasFaceUp = true;
                    for (int i = 0; i < SENSOR_SAMPLES; i++) {
                        mSamples[i] = false;
                    }
                }
            } else {
                // Check if its face down enough.
                mSamples[mSampleIndex] = (z < -GRAVITY_LOWER_THRESHOLD) &&
                    (z > -GRAVITY_UPPER_THRESHOLD);

                // face down
                if (filterSamples()) {
                    mStopped = true;
                    handleAction(mFlipAction);
                }
            }

            mSampleIndex = ((mSampleIndex + 1) % SENSOR_SAMPLES);
        }
    };

    private final SensorEventListener mShakeListener = new SensorEventListener() {
        private static final float SENSITIVITY = 16;
        private static final int BUFFER = 5;
        private float[] gravity = new float[3];
        private float average = 0;
        private int fill = 0;

        @Override
        public void onAccuracyChanged(Sensor sensor, int acc) {
        }

        public void onSensorChanged(SensorEvent event) {
            final float alpha = 0.8F;

            for (int i = 0; i < 3; i++) {
                gravity[i] = alpha * gravity[i] + (1 - alpha) * event.values[i];
            }

            float x = event.values[0] - gravity[0];
            float y = event.values[1] - gravity[1];
            float z = event.values[2] - gravity[2];

            if (fill <= BUFFER) {
                average += Math.abs(x) + Math.abs(y) + Math.abs(z);
                fill++;
            } else {
                if (average / BUFFER >= SENSITIVITY) {
                    handleAction(mShakeAction);
                }
                average = 0;
                fill = 0;
            }
        }
    };

    private void attachListeners() {
        if (mFlipAction != ALARM_NO_ACTION) {
            mFlipListener.reset();
            mSensorManager.registerListener(mFlipListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_NORMAL,
                    300 * 1000); //batch every 300 milliseconds
        }

        if (mShakeAction != ALARM_NO_ACTION) {
            mSensorManager.registerListener(mShakeListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_GAME,
                    50 * 1000); //batch every 50 milliseconds
        }
    }

    private void detachListeners() {
        if (mFlipAction != ALARM_NO_ACTION) {
            mSensorManager.unregisterListener(mFlipListener);
        }
        if (mShakeAction != ALARM_NO_ACTION) {
            mSensorManager.unregisterListener(mShakeListener);
        }
    }

    private void handleAction(int action) {
        switch (action) {
            case ALARM_SNOOZE:
                // Setup Snooze Action
                Intent snoozeIntent = AlarmStateManager.createStateChangeIntent(this, "SNOOZE_TAG",
                        mCurrentAlarm, AlarmInstance.SNOOZE_STATE);
                sendBroadcast(snoozeIntent);
                break;
            case ALARM_DISMISS:
                // Setup Dismiss Action
                Intent dismissIntent = AlarmStateManager.createStateChangeIntent(this,
                        "DISMISS_TAG", mCurrentAlarm, AlarmInstance.DISMISSED_STATE);
                sendBroadcast(dismissIntent);
                break;
            case ALARM_NO_ACTION:
            default:
                break;
        }
    }
}
