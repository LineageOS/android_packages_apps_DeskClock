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

package com.android.deskclock;

import android.Manifest;
import android.app.Activity;
import android.app.LoaderManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.deskclock.alarms.AlarmTimeClickHandler;
import com.android.deskclock.alarms.AlarmUpdateHandler;
import com.android.deskclock.alarms.ScrollHandler;
import com.android.deskclock.alarms.TimePickerCompat;
import com.android.deskclock.alarms.dataadapter.AlarmTimeAdapter;
import com.android.deskclock.data.DataModel;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.settings.DefaultAlarmToneDialog;
import com.android.deskclock.widget.EmptyViewController;
import com.android.deskclock.widget.toast.SnackbarManager;
import com.android.deskclock.widget.toast.ToastManager;

import java.util.List;

/**
 * A fragment that displays a list of alarm time and allows interaction with them.
 */
public final class AlarmClockFragment extends DeskClockFragment implements
        LoaderManager.LoaderCallbacks<Cursor>, ScrollHandler, TimePickerCompat.OnTimeSetListener {

    // This extra is used when receiving an intent to create an alarm, but no alarm details
    // have been passed in, so the alarm page should start the process of creating a new alarm.
    public static final String ALARM_CREATE_NEW_INTENT_EXTRA = "deskclock.create.new";

    // This extra is used when receiving an intent to scroll to specific alarm. If alarm
    // can not be found, and toast message will pop up that the alarm has be deleted.
    public static final String SCROLL_TO_ALARM_INTENT_EXTRA = "deskclock.scroll.to.alarm";

    public static final String SEL_AUDIO_SRC = "audio/*";
    public static final int SEL_SRC_RINGTONE = 0;
    public static final int SEL_SRC_EXTERNAL = 1;
    //extend request index is from 10 start
    public static final int REQUEST_CODE_RINGTONE = 10;
    public static final int REQUEST_CODE_PERMISSIONS = 11;
    public static final int REQUEST_CODE_EXTERN_AUDIO = 12;
    public static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION = 13;
    public static final int REQUEST_CODE_WRITE_SETTINGS = 14;
    private Uri mWaitUpdateUri;

    private static final String QUERY_URI = "content://com.android.deskclock/alarms";
    private String old_default_ringtone_uri;
    private String new_default_ringtone_Uri;
    private RefreshDefaultRingtoneBroadcastReceiver mReceiver;

    // Views
    private ViewGroup mMainLayout;
    private RecyclerView mRecyclerView;

    // Data
    private long mScrollToAlarmId = Alarm.INVALID_ID;
    private Loader mCursorLoader = null;

    // Controllers
    private AlarmTimeAdapter mAlarmTimeAdapter;
    private AlarmUpdateHandler mAlarmUpdateHandler;
    private EmptyViewController mEmptyViewController;
    private AlarmTimeClickHandler mAlarmTimeClickHandler;
    private LinearLayoutManager mLayoutManager;

    @Override
    public void processTimeSet(int hourOfDay, int minute) {
        mAlarmTimeClickHandler.processTimeSet(hourOfDay, minute);
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        mCursorLoader = getLoaderManager().initLoader(0, null, this);

        mReceiver = new RefreshDefaultRingtoneBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(DefaultAlarmToneDialog.REFRESH_DEFAULT_RINGTONE_ACTION);
        getActivity().registerReceiver(mReceiver, filter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        // Inflate the layout for this fragment
        final View v = inflater.inflate(R.layout.alarm_clock, container, false);

        mRecyclerView = (RecyclerView) v.findViewById(R.id.alarms_recycler_view);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mMainLayout = (ViewGroup) v.findViewById(R.id.main);
        mAlarmUpdateHandler = new AlarmUpdateHandler(getActivity(), this, mMainLayout);
        mEmptyViewController = new EmptyViewController(mMainLayout, mRecyclerView,
                v.findViewById(R.id.alarms_empty_view));
        mAlarmTimeClickHandler = new AlarmTimeClickHandler(this, savedState, mAlarmUpdateHandler,
                this);
        mAlarmTimeAdapter = new AlarmTimeAdapter(getActivity(), savedState,
                mAlarmTimeClickHandler, this);
        mRecyclerView.setAdapter(mAlarmTimeAdapter);

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();

        final DeskClock activity = (DeskClock) getActivity();
        if (activity.getSelectedTab() == DeskClock.ALARM_TAB_INDEX) {
            setFabAppearance();
            setLeftRightButtonAppearance();
        }

        // Check if another app asked us to create a blank new alarm.
        final Intent intent = getActivity().getIntent();
        if (intent.hasExtra(ALARM_CREATE_NEW_INTENT_EXTRA)) {
            if (intent.getBooleanExtra(ALARM_CREATE_NEW_INTENT_EXTRA, false)) {
                // An external app asked us to create a blank alarm.
                startCreatingAlarm();
            }

            // Remove the CREATE_NEW extra now that we've processed it.
            intent.removeExtra(ALARM_CREATE_NEW_INTENT_EXTRA);
        } else if (intent.hasExtra(SCROLL_TO_ALARM_INTENT_EXTRA)) {
            long alarmId = intent.getLongExtra(SCROLL_TO_ALARM_INTENT_EXTRA, Alarm.INVALID_ID);
            if (alarmId != Alarm.INVALID_ID) {
                setSmoothScrollStableId(alarmId);
                if (mCursorLoader != null && mCursorLoader.isStarted()) {
                    // We need to force a reload here to make sure we have the latest view
                    // of the data to scroll to.
                    mCursorLoader.forceLoad();
                }
            }

            // Remove the SCROLL_TO_ALARM extra now that we've processed it.
            intent.removeExtra(SCROLL_TO_ALARM_INTENT_EXTRA);
        }
    }

    @Override
    public void smoothScrollTo(int position) {
        mLayoutManager.scrollToPositionWithOffset(position, 20);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mAlarmTimeAdapter.saveInstance(outState);
        mAlarmTimeClickHandler.saveInstance(outState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ToastManager.cancelToast();

        getActivity().unregisterReceiver(mReceiver);
    }

    @Override
    public void onPause() {
        super.onPause();
        // When the user places the app in the background by pressing "home",
        // dismiss the toast bar. However, since there is no way to determine if
        // home was pressed, just dismiss any existing toast bar when restarting
        // the app.
        mAlarmUpdateHandler.hideUndoBar();
    }

    public void setLabel(Alarm alarm, String label) {
        alarm.label = label;
        mAlarmUpdateHandler.asyncUpdateAlarm(alarm, false, true);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return Alarm.getAlarmsCursorLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, final Cursor data) {
        mEmptyViewController.setEmpty(data.getCount() == 0);
        mAlarmTimeAdapter.swapCursor(data);
        if (mScrollToAlarmId != Alarm.INVALID_ID) {
            scrollToAlarm(mScrollToAlarmId);
            setSmoothScrollStableId(Alarm.INVALID_ID);
        }
    }

    /**
     * Scroll to alarm with given alarm id.
     *
     * @param alarmId The alarm id to scroll to.
     */
    private void scrollToAlarm(long alarmId) {
        final int alarmCount = mAlarmTimeAdapter.getItemCount();
        int alarmPosition = -1;
        for (int i = 0; i < alarmCount; i++) {
            long id = mAlarmTimeAdapter.getItemId(i);
            if (id == alarmId) {
                alarmPosition = i;
                break;
            }
        }

        if (alarmPosition >= 0) {
            mAlarmTimeAdapter.expand(alarmPosition);
        } else {
            // Trying to display a deleted alarm should only happen from a missed notification for
            // an alarm that has been marked deleted after use.
            SnackbarManager.show(Snackbar.make(mMainLayout, R.string
                    .missed_alarm_has_been_deleted, Snackbar.LENGTH_LONG));
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mAlarmTimeAdapter.swapCursor(null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case R.id.request_code_ringtone:
            case REQUEST_CODE_RINGTONE:
            case REQUEST_CODE_EXTERN_AUDIO:
                // Extract the selected ringtone uri.

                Uri uri = null;
                if (requestCode == REQUEST_CODE_EXTERN_AUDIO) {
                    uri = data.getData();
                    LogUtils.d(LogUtils.LOGTAG,
                            "AlarmClockFragment:onActivityResult: music uri = " + uri);

                    // If the user chose an external ringtone and has not yet granted the permission
                    // to read external storage, ask them for that permission now.
                    if (!AlarmUtils.hasPermissionToDisplayRingtoneTitle(getActivity(), uri)) {
                        mWaitUpdateUri = uri;
                        final String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
                        requestPermissions(perms, REQUEST_READ_EXTERNAL_STORAGE_PERMISSION);
                    } else {
                        updateSelectAlarmRingToneUri(uri);
                    }
                } else {
                    uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    LogUtils.d(LogUtils.LOGTAG,
                            "AlarmClockFragment:onActivityResult: ringtone uri = " + uri);
                    updateSelectAlarmRingToneUri(uri);
                }
                break;
            case REQUEST_CODE_WRITE_SETTINGS:
                if (Settings.System.canWrite(getContext())) {
                    DataModel.getDataModel().setDefaultAlarmRingtoneUri(
                            Uri.parse(new_default_ringtone_Uri));
                }
                break;
            default:
                LogUtils.w("Unhandled request code in onActivityResult: " + requestCode);
        }
    }

    @Override
    public void setSmoothScrollStableId(long stableId) {
        mScrollToAlarmId = stableId;
    }

    @Override
    public void onFabClick(View view) {
        mAlarmUpdateHandler.hideUndoBar();
        startCreatingAlarm();
    }

    @Override
    public void setFabAppearance() {
        if (mFab == null || getDeskClock().getSelectedTab() != DeskClock.ALARM_TAB_INDEX) {
            return;
        }
        mFab.setVisibility(View.VISIBLE);
        mFab.setImageResource(R.drawable.ic_add_white_24dp);
        mFab.setContentDescription(getString(R.string.button_alarms));
    }

    @Override
    public void setLeftRightButtonAppearance() {
        if (mLeftButton == null || mRightButton == null ||
                getDeskClock().getSelectedTab() != DeskClock.ALARM_TAB_INDEX) {
            return;
        }
        mLeftButton.setVisibility(View.INVISIBLE);
        mRightButton.setVisibility(View.INVISIBLE);
    }

    private void startCreatingAlarm() {
        mAlarmTimeClickHandler.clearSelectedAlarm();
        TimePickerCompat.showTimeEditDialog(this, null /* alarm */,
                DateFormat.is24HourFormat(getActivity()));
    }

    private class RefreshDefaultRingtoneBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            old_default_ringtone_uri = intent
                    .getStringExtra(DefaultAlarmToneDialog.OLD_RING_TONE_URI_STRING);
            new_default_ringtone_Uri = intent
                    .getStringExtra(DefaultAlarmToneDialog.NEW_RING_TONE_URI_STRING);

            LogUtils.d(LogUtils.LOGTAG, "old_default_ringtone_uri = " + old_default_ringtone_uri
                    + " new_default_ringtone_Uri = " + new_default_ringtone_Uri);

            updateAlarmRingTone(new_default_ringtone_Uri, old_default_ringtone_uri);

            // Update the default ringtone for future new alarms.
            DataModel.getDataModel().setDefaultAlarmRingtoneUri(Uri.parse(new_default_ringtone_Uri));
            if (!Settings.System.canWrite(context)) {
                Intent settingIntent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + context.getPackageName()));
                try {
                    startActivityForResult(settingIntent, REQUEST_CODE_WRITE_SETTINGS);
                } catch (ActivityNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void updateAlarmRingTone(String newRingtone, String oldRingtone) {
        Context accessContext = getActivity().getApplicationContext();
        ContentResolver cr = accessContext.getContentResolver();

        String selection = "ringtone = ?";
        String[] selectionArgs;

        if(oldRingtone != null
                && !oldRingtone.equals(Settings.System.DEFAULT_ALARM_ALERT_URI.toString())) {
            selectionArgs = new String[] {oldRingtone};
        } else {
            selectionArgs = new String[] {Settings.System.DEFAULT_ALARM_ALERT_URI.toString()};
        }

        LogUtils.d(LogUtils.LOGTAG, "updateAlarmRingTone: selectionArgs = " + selectionArgs[0]);

        Cursor cursor = cr.query(
                Uri.parse(QUERY_URI),
                new String[] { "*" }, selection, selectionArgs, null);

        Alarm a = null;
        if (cursor != null && cursor.getCount() > 0) {
            LogUtils.d(LogUtils.LOGTAG, "updateAlarmRingTone cursor.getCount() = " + cursor.getCount());
            while (cursor.moveToNext()) {
                a = new Alarm(cursor, Uri.parse(newRingtone));
                LogUtils.d(LogUtils.LOGTAG, "The update alarm is " + a);
                mAlarmUpdateHandler.asyncUpdateAlarm(a, false /* popToast */,
                        true /* minorUpdate */);
                a = null;
            }
        }

        if (cursor != null || cursor.getCount() == 0) {
            cursor.close();
        }
    }

    private List<Alarm> getAlarmsByUri(Context context, Uri uri) {
       final String selection = String.format("%s=?", Alarm.RINGTONE);
       final String[] args = { uri.toString() };
       return Alarm.getAlarms(context.getContentResolver(), selection, args);
    }

    private void updateSelectAlarmRingToneUri(Uri uri) {
        if (uri == null) {
            uri = Alarm.NO_RINGTONE_URI;
        }

        // Set the ringtone uri on the alarm.
        final Alarm alarm = mAlarmTimeClickHandler.getSelectedAlarm();
        if (alarm == null) {
            LogUtils.e("Could not get selected alarm to set ringtone");
            return;
        }

        // If the selected uri doesn't equals to the original uri, we need
        // to check if the new uir is document uri and take the persistable
        // permission.
        // If there is no other alarm which uses the original uri and it is
        // not the default alarm ringtone uri, then we need to release the
        // permission of the orignal uri.
        if (uri != alarm.alert) {
            Context context = getActivity().getApplicationContext();
            ContentResolver resolver = context.getContentResolver();
            final int takeFlag = Intent.FLAG_GRANT_READ_URI_PERMISSION;

            SharedPreferences sharedPref = Utils.getCESharedPreferences(context);
            String defaultRingTone = sharedPref.getString(
                    DefaultAlarmToneDialog.DEFAULT_RING_TONE_URI_KEY,
                    DefaultAlarmToneDialog.DEFAULT_RING_TONE_DEFAULT);
            Uri defaultUri = Uri.parse(defaultRingTone);

            if (DocumentsContract.isDocumentUri(context, alarm.alert) &&
                    alarm.alert != defaultUri && getAlarmsByUri(context, alarm.alert).size() == 1) {
                try {
                    resolver.releasePersistableUriPermission(alarm.alert, takeFlag);
                } catch (Exception ex) {
                    LogUtils.e("releasePersistableUriPermission exception : "+ ex);
                }
            }

            if (DocumentsContract.isDocumentUri(context, uri)) {
                // Check for the freshest data and take persist permission.
                try {
                    resolver.takePersistableUriPermission(uri, takeFlag);
                } catch (Exception ex) {
                    LogUtils.e("takePersistableUriPermission exception : " + ex);
                }
            }
        }

        alarm.alert = uri;

        // Save the change to alarm.
        mAlarmUpdateHandler.asyncUpdateAlarm(alarm, false /* popToast */,
                true /* minorUpdate */);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        switch (requestCode) {
            case REQUEST_READ_EXTERNAL_STORAGE_PERMISSION:
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LogUtils.d(LogUtils.LOGTAG, "onRequestPermissionsResult: mWaitUpdateUri = "
                            + mWaitUpdateUri);
                    updateSelectAlarmRingToneUri(mWaitUpdateUri);
                } else {
                    //Toast you denied the external storage permission
                    Toast.makeText(getActivity(), getString(R.string.have_denied_storage_permission),
                            Toast.LENGTH_LONG).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }
}
