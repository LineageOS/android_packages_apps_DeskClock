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

import android.app.PendingIntent;
import android.app.Profile;
import android.app.ProfileManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.android.deskclock.AlarmAlertWakeLock;
import com.android.deskclock.LogUtils;
import com.android.deskclock.SettingsActivity;
import com.android.deskclock.provider.AlarmInstance;
import com.android.deskclock.R;

/**
 * This service is in charge of starting/stoping the alarm. It will bring up and manage the
 * {@link AlarmActivity} as well as {@link AlarmKlaxon}.
 */
public class AlarmService extends Service {
    // A public action send by AlarmService when the alarm has started.
    public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";

    // A public action sent by AlarmService when the alarm has stopped for any reason.
    public static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";

    // Private action used to start an alarm with this service.
    public static final String START_ALARM_ACTION = "START_ALARM";

    // Private action used to stop an alarm with this service.
    public static final String STOP_ALARM_ACTION = "STOP_ALARM";

    // default action for flip and shake
    private static final String DEFAULT_ACTION = "0";

    // constants for no action/snooze/dismiss
    private static final int ALARM_NO_ACTION = 0;
    private static final int ALARM_SNOOZE = 1;
    private static final int ALARM_DISMISS = 2;
    private String mVolumeBehavior;

    /**
     * Utility method to help start alarm properly. If alarm is already firing, it
     * will mark it as missed and start the new one.
     *
     * @param context application context
     * @param instance to trigger alarm
     */
    public static void startAlarm(Context context, AlarmInstance instance) {
        Intent intent = AlarmInstance.createIntent(context, AlarmService.class, instance.mId);
        intent.setAction(START_ALARM_ACTION);

        // Maintain a cpu wake lock until the service can get it
        AlarmAlertWakeLock.acquireCpuWakeLock(context);
        context.startService(intent);
    }

    /**
     * Utility method to help stop an alarm properly. Nothing will happen, if alarm is not firing
     * or using a different instance.
     *
     * @param context application context
     * @param instance you are trying to stop
     */
    public static void stopAlarm(Context context, AlarmInstance instance) {
        Intent intent = AlarmInstance.createIntent(context, AlarmService.class, instance.mId);
        intent.setAction(STOP_ALARM_ACTION);

        // We don't need a wake lock here, since we are trying to kill an alarm
        context.startService(intent);
    }

    private TelephonyManager mTelephonyManager;
    private int mInitialCallState;
    private AlarmInstance mCurrentAlarm = null;
    private SensorManager mSensorManager;
    private int mFlipAction;
    private int mShakeAction;

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
            } else if (state != TelephonyManager.CALL_STATE_IDLE && state != mInitialCallState) {
                sendBroadcast(AlarmStateManager.createStateChangeIntent(AlarmService.this,
                        "AlarmService", mCurrentAlarm, AlarmInstance.MISSED_STATE));
                stopCurrentAlarm();
            }
        }
    };

    private void changeToProfile(final Context context, final AlarmInstance instance) {
        // The alarm is defined to change the active profile?
        if (instance.mProfile.equals(ProfileManager.NO_PROFILE)) {
            LogUtils.v("Alarm doesn't define a profile to change to");
            return;
        }

        final ProfileManager profileManager =
                (ProfileManager) context.getSystemService(Context.PROFILE_SERVICE);
        boolean isProfilesEnabled = Settings.System.getInt(context.getContentResolver(),
                Settings.System.SYSTEM_PROFILES_ENABLED, 1) == 1;
        if (!isProfilesEnabled) {
            LogUtils.v("Profiles are disabled");
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

    private void startAlarm(AlarmInstance instance) {
        LogUtils.v("AlarmService.start with instance: " + instance.mId);
        if (mCurrentAlarm != null) {
            AlarmStateManager.setMissedState(this, mCurrentAlarm);
            stopCurrentAlarm();
        }

        AlarmAlertWakeLock.acquireCpuWakeLock(this);
        mCurrentAlarm = instance;

        switch (mVolumeBehavior) {
            case SettingsActivity.VOLUME_BEHAVIOR_SNOOZE:
            case SettingsActivity.VOLUME_BEHAVIOR_DISMISS:
                Intent fullScreenIntent = AlarmInstance.createIntent(this, AlarmActivity.class,
                        instance.mId);
                // set action, so we can be different then content pending intent
                fullScreenIntent.setAction("fullscreen_activity");
                fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION);
                startActivity(fullScreenIntent);
                break;
            default:
                AlarmNotifications.showAlarmNotification(this, mCurrentAlarm);
                break;
        }

        mInitialCallState = mTelephonyManager.getCallState();
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        boolean inCall = mInitialCallState != TelephonyManager.CALL_STATE_IDLE;
        AlarmKlaxon.start(this, mCurrentAlarm, inCall);
        changeToProfile(this, mCurrentAlarm);
        sendBroadcast(new Intent(ALARM_ALERT_ACTION));
        attachListeners();
    }

    private void stopCurrentAlarm() {
        if (mCurrentAlarm == null) {
            LogUtils.v("There is no current alarm to stop");
            return;
        }

        LogUtils.v("AlarmService.stop with instance: " + mCurrentAlarm.mId);
        AlarmKlaxon.stop(this);
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        sendBroadcast(new Intent(ALARM_DONE_ACTION));
        mCurrentAlarm = null;
        detachListeners();
        AlarmAlertWakeLock.releaseCpuLock();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mFlipAction = Integer.parseInt(prefs.getString(
                SettingsActivity.KEY_FLIP_ACTION, DEFAULT_ACTION));
        mShakeAction = Integer.parseInt(prefs.getString(
                SettingsActivity.KEY_SHAKE_ACTION, DEFAULT_ACTION));
        mVolumeBehavior = prefs.getString(SettingsActivity.KEY_VOLUME_BEHAVIOR,
                SettingsActivity.DEFAULT_VOLUME_BEHAVIOR);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.v("AlarmService.onStartCommand() with intent: " + intent.toString());

        long instanceId = AlarmInstance.getId(intent.getData());
        if (START_ALARM_ACTION.equals(intent.getAction())) {
            ContentResolver cr = this.getContentResolver();
            AlarmInstance instance = AlarmInstance.getInstance(cr, instanceId);
            if (instance == null) {
                LogUtils.e("No instance found to start alarm: " + instanceId);
                if (mCurrentAlarm != null) {
                    // Only release lock if we are not firing alarm
                    AlarmAlertWakeLock.releaseCpuLock();
                }
                return Service.START_NOT_STICKY;
            } else if (mCurrentAlarm != null && mCurrentAlarm.mId == instanceId) {
                LogUtils.e("Alarm already started for instance: " + instanceId);
                return Service.START_NOT_STICKY;
            }
            startAlarm(instance);
        } else if(STOP_ALARM_ACTION.equals(intent.getAction())) {
            if (mCurrentAlarm != null && mCurrentAlarm.mId != instanceId) {
                LogUtils.e("Can't stop alarm for instance: " + instanceId +
                        " because current alarm is: " + mCurrentAlarm.mId);
                return Service.START_NOT_STICKY;
            }
            stopSelf();
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        LogUtils.v("AlarmService.onDestroy() called");
        super.onDestroy();
        stopCurrentAlarm();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final SensorEventListener mFlipListener = new SensorEventListener() {
        private static final int FACE_UP_LOWER_LIMIT = -45;
        private static final int FACE_UP_UPPER_LIMIT = 45;
        private static final int FACE_DOWN_UPPER_LIMIT = 135;
        private static final int FACE_DOWN_LOWER_LIMIT = -135;
        private static final int TILT_UPPER_LIMIT = 45;
        private static final int TILT_LOWER_LIMIT = -45;
        private static final int SENSOR_SAMPLES = 3;

        private boolean mWasFaceUp;
        private boolean[] mSamples = new boolean[SENSOR_SAMPLES];
        private int mSampleIndex;

        @Override
        public void onAccuracyChanged(Sensor sensor, int acc) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // Add a sample overwriting the oldest one. Several samples
            // are used
            // to avoid the erroneous values the sensor sometimes
            // returns.
            float y = event.values[1];
            float z = event.values[2];

            if (!mWasFaceUp) {
                // Check if its face up enough.
                mSamples[mSampleIndex] = y > FACE_UP_LOWER_LIMIT
                        && y < FACE_UP_UPPER_LIMIT
                        && z > TILT_LOWER_LIMIT && z < TILT_UPPER_LIMIT;

                // The device first needs to be face up.
                boolean faceUp = true;
                for (boolean sample : mSamples) {
                    faceUp = faceUp && sample;
                }
                if (faceUp) {
                    mWasFaceUp = true;
                    for (int i = 0; i < SENSOR_SAMPLES; i++) {
                        mSamples[i] = false;
                    }
                }
            } else {
                // Check if its face down enough. Note that wanted
                // values go from FACE_DOWN_UPPER_LIMIT to 180
                // and from -180 to FACE_DOWN_LOWER_LIMIT
                mSamples[mSampleIndex] = (y > FACE_DOWN_UPPER_LIMIT || y < FACE_DOWN_LOWER_LIMIT)
                        && z > TILT_LOWER_LIMIT
                        && z < TILT_UPPER_LIMIT;

                boolean faceDown = true;
                for (boolean sample : mSamples) {
                    faceDown = faceDown && sample;
                }
                if (faceDown) {
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
            mSensorManager.registerListener(mFlipListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
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
                Intent dismissIntent = AlarmStateManager.createStateChangeIntent(this, "DISMISS_TAG",
                        mCurrentAlarm, AlarmInstance.DISMISSED_STATE);
                sendBroadcast(dismissIntent);
                break;
            case ALARM_NO_ACTION:
            default:
                break;
        }
    }

}
