/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.format.DateFormat;

import com.android.deskclock.AlarmClockFragment;
import com.android.deskclock.LabelDialogFragment;
import com.android.deskclock.LogUtils;
import com.android.deskclock.R;
import com.android.deskclock.alarms.utils.DayOrderUtils;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.provider.AlarmInstance;

import java.util.Calendar;

/**
 * Click handler for an alarm time item.
 */
public final class AlarmTimeClickHandler {

    private static final String TAG = "AlarmTimeClickHandler";
    private static final String KEY_PREVIOUS_DAY_MAP = "previousDayMap";

    private int mSelectSource = AlarmClockFragment.SEL_SRC_RINGTONE;
    private static final String KEY_SELECT_SOURCE = "selectedSource";
    private static final String KEY_SELECTED_ALARM = "selectedAlarm";

    private final Fragment mFragment;
    public final AlarmUpdateHandler mAlarmUpdateHandler;
    private final ScrollHandler mScrollHandler;

    private Alarm mSelectedAlarm;
    private Bundle mPreviousDaysOfWeekMap;
    private int[] mDayOrder;

    public AlarmTimeClickHandler(Fragment fragment, Bundle savedState,
            AlarmUpdateHandler alarmUpdateHandler, ScrollHandler smoothScrollController) {
        mFragment = fragment;
        mAlarmUpdateHandler = alarmUpdateHandler;
        mScrollHandler = smoothScrollController;
        if (savedState != null) {
            mPreviousDaysOfWeekMap = savedState.getBundle(KEY_PREVIOUS_DAY_MAP);
            mSelectedAlarm = savedState.getParcelable(KEY_SELECTED_ALARM);
        }
        if (mPreviousDaysOfWeekMap == null) {
            mPreviousDaysOfWeekMap = new Bundle();
        }
        mDayOrder = DayOrderUtils.getDayOrder(fragment.getActivity());
    }

    public Alarm getSelectedAlarm() {
        return mSelectedAlarm;
    }

    public void clearSelectedAlarm() {
        mSelectedAlarm = null;
    }

    public void saveInstance(Bundle outState) {
        outState.putBundle(KEY_PREVIOUS_DAY_MAP, mPreviousDaysOfWeekMap);
        outState.putInt(KEY_SELECT_SOURCE, mSelectSource);
        outState.putParcelable(KEY_SELECTED_ALARM, mSelectedAlarm);
    }

    public void setAlarmEnabled(Alarm alarm, boolean newState) {
        if (newState != alarm.enabled) {
            alarm.enabled = newState;
            mAlarmUpdateHandler.asyncUpdateAlarm(alarm, alarm.enabled, false);
            LogUtils.d(TAG, "Updating alarm enabled state to " + newState);
        }
    }

    public void setAlarmVibrationEnabled(Alarm alarm, boolean newState) {
        if (newState != alarm.vibrate) {
            alarm.vibrate = newState;
            mAlarmUpdateHandler.asyncUpdateAlarm(alarm, false, true);
            LogUtils.d(TAG, "Updating vibrate state to " + newState);

            if (newState) {
                // Buzz the vibrator to preview the alarm firing behavior.
                final Context context = mFragment.getActivity();
                final Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (v.hasVibrator()) {
                    v.vibrate(300);
                }
            }
        }
    }

    public void setAlarmRepeatEnabled(Alarm alarm, boolean isEnabled) {
        final Calendar now = Calendar.getInstance();
        final Calendar oldNextAlarmTime = alarm.getNextAlarmTime(now);
        final String alarmId = String.valueOf(alarm.id);
        if (isEnabled) {
            // Set all previously set days
            // or
            // Set all days if no previous.
            final int bitSet = mPreviousDaysOfWeekMap.getInt(alarmId);
            alarm.daysOfWeek.setBitSet(bitSet);
            if (!alarm.daysOfWeek.isRepeating()) {
                alarm.daysOfWeek.setDaysOfWeek(true, mDayOrder);
            }
        } else {
            // Remember the set days in case the user wants it back.
            final int bitSet = alarm.daysOfWeek.getBitSet();
            mPreviousDaysOfWeekMap.putInt(alarmId, bitSet);

            // Remove all repeat days
            alarm.daysOfWeek.clearAllDays();
        }

        // if the change altered the next scheduled alarm time, tell the user
        final Calendar newNextAlarmTime = alarm.getNextAlarmTime(now);
        final boolean popupToast = !oldNextAlarmTime.equals(newNextAlarmTime);

        mAlarmUpdateHandler.asyncUpdateAlarm(alarm, popupToast, false);
    }

    public void setDayOfWeekEnabled(Alarm alarm, boolean checked, int index) {
        final Calendar now = Calendar.getInstance();
        final Calendar oldNextAlarmTime = alarm.getNextAlarmTime(now);
        alarm.daysOfWeek.setDaysOfWeek(checked, mDayOrder[index]);
        // if the change altered the next scheduled alarm time, tell the user
        final Calendar newNextAlarmTime = alarm.getNextAlarmTime(now);
        final boolean popupToast = !oldNextAlarmTime.equals(newNextAlarmTime);
        mAlarmUpdateHandler.asyncUpdateAlarm(alarm, popupToast, false);
    }

    public void onDeleteClicked(Alarm alarm) {
        mAlarmUpdateHandler.asyncDeleteAlarm(alarm);
        LogUtils.d(TAG, "Deleting alarm.");
    }

    public void onClockClicked(Alarm alarm) {
        mSelectedAlarm = alarm;
        TimePickerCompat.showTimeEditDialog(mFragment, alarm,
                DateFormat.is24HourFormat(mFragment.getActivity()));
    }

    public void dismissAlarmInstance(AlarmInstance alarmInstance) {
        final Context context = mFragment.getActivity().getApplicationContext();
        final Intent dismissIntent = AlarmStateManager.createStateChangeIntent(
                context, AlarmStateManager.ALARM_DISMISS_TAG, alarmInstance,
                AlarmInstance.PREDISMISSED_STATE);
        context.startService(dismissIntent);
        mAlarmUpdateHandler.showPredismissToast(alarmInstance);
    }

    public void onRingtoneClicked(Alarm alarm) {
        launchRingTonePicker(alarm);
    }

    public void onEditLabelClicked(Alarm alarm) {
        final FragmentTransaction ft = mFragment.getFragmentManager().beginTransaction();
        final Fragment prev = mFragment.getFragmentManager().findFragmentByTag("label_dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        // Create and show the dialog.
        final LabelDialogFragment newFragment =
                LabelDialogFragment.newInstance(alarm, alarm.label, mFragment.getTag());
        newFragment.show(ft, "label_dialog");
    }

    public void processTimeSet(int hourOfDay, int minute) {
        if (mSelectedAlarm == null) {
            // If mSelectedAlarm is null then we're creating a new alarm.
            final Alarm a = new Alarm();
            a.hour = hourOfDay;
            a.minutes = minute;
            a.enabled = true;
            mAlarmUpdateHandler.asyncAddAlarm(a);
        } else {
            mSelectedAlarm.hour = hourOfDay;
            mSelectedAlarm.minutes = minute;
            mSelectedAlarm.enabled = true;
            mScrollHandler.setSmoothScrollStableId(mSelectedAlarm.id);
            mAlarmUpdateHandler.asyncUpdateAlarm(mSelectedAlarm, true, false);
            mSelectedAlarm = null;
        }
    }

    private void launchRingTonePicker(Alarm alarm) {
        mSelectedAlarm = alarm;
        RingTonePickerDialogListener listener = new RingTonePickerDialogListener((AlarmClockFragment)mFragment);
        new AlertDialog.Builder(mFragment.getActivity())
                .setTitle(mFragment.getResources().getString(R.string.alarm_select))
                .setItems(
                        new String[] {
                                mFragment.getResources().getString(
                                        R.string.alarm_select_ringtone),
                                mFragment.getResources().getString(
                                        R.string.alarm_select_external) },
                        listener).show();
    }

    private class RingTonePickerDialogListener implements DialogInterface.OnClickListener {
        private AlarmClockFragment alarm;

        public RingTonePickerDialogListener(AlarmClockFragment clock) {
            alarm = clock;
        }

        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case AlarmClockFragment.SEL_SRC_RINGTONE:
                case AlarmClockFragment.SEL_SRC_EXTERNAL:
                    mSelectSource = which;
                    sendPickIntent();
                    break;
                default:
                    dialog.dismiss();
                    break;
            }
        }
    }

    private void sendPickIntent() {
        LogUtils.d(TAG, "sendPickIntent is called, mSelectSource = " + mSelectSource);
        if (mSelectSource == AlarmClockFragment.SEL_SRC_RINGTONE) {
            final Uri oldRingtone = Alarm.NO_RINGTONE_URI.equals(mSelectedAlarm.alert) ? null : mSelectedAlarm.alert;
            final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, oldRingtone);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
            LogUtils.d(TAG, "Showing ringtone picker.");
            mFragment.startActivityForResult(intent, AlarmClockFragment.REQUEST_CODE_RINGTONE);
        } else {
            // As the minSdkVersion for this app is 19, we don't need to add sdk version check
            // here to use ACTION_OPEN_DOCUMENT.
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, mSelectedAlarm.alert);
            intent.setType(AlarmClockFragment.SEL_AUDIO_SRC);
            mFragment.startActivityForResult(intent, AlarmClockFragment.REQUEST_CODE_EXTERN_AUDIO);
        }
    }
}
