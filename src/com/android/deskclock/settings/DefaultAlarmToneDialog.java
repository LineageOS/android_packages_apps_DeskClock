/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.deskclock.settings;

import com.android.deskclock.AlarmClockFragment;
import com.android.deskclock.LogUtils;
import com.android.deskclock.R;
import com.android.deskclock.Utils;
import com.android.deskclock.data.DataModel;
import com.android.deskclock.provider.Alarm;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.DialogPreference;
import android.provider.DocumentsContract;
import android.util.AttributeSet;

public class DefaultAlarmToneDialog extends DialogPreference {
    private int mSelectSource;
    private String mRingtone;
    private Boolean showDefaultRingtone;
    private static Uri oldRingTone;
    // set default of default_ring_tone
    public static final String DEFAULT_RING_TONE_DEFAULT = "content://media/internal/audio/media/9";
    // the key of default_ring_tone‘default from sharedpreference
    public static final String DEFAULT_RING_TONE_URI_KEY = "defaultRingTone_uri";
    // the key of defaultRingTone to show (set summary) from sharepredference
    public static final String DEFAULT_RING_TONE_NAME_KEY = "defaultRingTone_name";
    public static final String DEFAULT_RING_TONE_NAME = "Cesium";
    public static final String OLD_RING_TONE_URI_STRING = "old_uri_toString";
    public static final String NEW_RING_TONE_URI_STRING = "new_uri_toString";
    public static final String REFRESH_DEFAULT_RINGTONE_ACTION =
            "com.android.deskclock.action.REFRSH_DEFAULT_RING_TONE";
    public static final String SETTING_SYSTEM_RINGTONE = "content://settings/system/ringtone";
    private SharedPreferences mSharedPref;
    private Context mContext;

    public DefaultAlarmToneDialog(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
        mSharedPref = Utils.getCESharedPreferences(context);

        //reset to the default ringtone when the current ringtone has been deleted
        String defaultRingTone = mSharedPref.getString(DEFAULT_RING_TONE_URI_KEY,
                DEFAULT_RING_TONE_DEFAULT);
        Uri alert = DataModel.getDataModel().getDefaultAlarmRingtoneUri();
        if (alert != null) {
            defaultRingTone = alert.toString();
        }

        if ("".equalsIgnoreCase(defaultRingTone)) {
            LogUtils.d(LogUtils.LOGTAG, "DefaultAlarmToneDialog: defaultRingTone is empty");
        } else if(!Utils.isRingToneUriValid(context, Uri.parse(defaultRingTone))) {
            mSharedPref.edit().putString(DEFAULT_RING_TONE_URI_KEY, DEFAULT_RING_TONE_DEFAULT).apply();
            String displayNameString = getRingtoneString(Uri.parse(DEFAULT_RING_TONE_DEFAULT));
            mSharedPref.edit().putString(DEFAULT_RING_TONE_NAME_KEY, displayNameString).apply();
            DataModel.getDataModel().setDefaultAlarmRingtoneUri(
                    Uri.parse(DEFAULT_RING_TONE_DEFAULT));
        } else if (defaultRingTone != null) {
            mSharedPref.edit().putString(DEFAULT_RING_TONE_URI_KEY, defaultRingTone).apply();
            String displayNameString = DataModel.getDataModel().getAlarmRingtoneTitle(alert);
            mSharedPref.edit().putString(DEFAULT_RING_TONE_NAME_KEY, displayNameString).apply();
        }

        if (mSharedPref.getString(DEFAULT_RING_TONE_NAME_KEY, null) == null) {
            LogUtils.d(LogUtils.LOGTAG, "DefaultAlarmToneDialog: summary = null");
            setSummary(DEFAULT_RING_TONE_NAME);
        }
    }

    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setTitle(
                mContext.getResources().getString(R.string.alarm_select))
                .setItems(
                        new String[] {
                                mContext.getResources().getString(
                                        R.string.alarm_select_ringtone),
                                mContext.getResources().getString(
                                        R.string.alarm_select_external) },
                        new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                switch (which) {
                                case AlarmClockFragment.SEL_SRC_RINGTONE:
                                case AlarmClockFragment.SEL_SRC_EXTERNAL:
                                    mSelectSource = which;
                                    sendPickIntent(which);
                                    break;
                                default:
                                    dialog.dismiss();
                                    break;
                                }
                            }
                        }).setPositiveButton(null, null)
                .setNegativeButton(null, null).setCancelable(true);
    }

    private void sendPickIntent(int selectSource) {
        SettingsActivity activity = (SettingsActivity) mContext;
        String defaultRingTone = mSharedPref.getString(DEFAULT_RING_TONE_URI_KEY, DEFAULT_RING_TONE_DEFAULT);
        if ("".equalsIgnoreCase(defaultRingTone)) {
            oldRingTone = null;
        } else {
            oldRingTone = Uri.parse(defaultRingTone);
        }

        LogUtils.d(LogUtils.LOGTAG, "defaultAlarmToneDialog sendPickIntent oldRingTone = " + oldRingTone);
        if (selectSource == AlarmClockFragment.SEL_SRC_RINGTONE) {
            final Intent intent = new Intent(
                    RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    oldRingTone);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_ALARM);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
            activity.startActivityForResult(intent,
                    AlarmClockFragment.REQUEST_CODE_RINGTONE);
        } else {
            // As the minSdkVersion for this app is 19, we don't need to add sdk version check
            // here to use ACTION_OPEN_DOCUMENT.
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    oldRingTone);
            intent.setType(AlarmClockFragment.SEL_AUDIO_SRC);
            activity.startActivityForResult(intent,
                    AlarmClockFragment.REQUEST_CODE_EXTERN_AUDIO);
        }
    }

    public void saveRingtoneUri(Intent intent) {
        Uri uri = getRingtoneUri(intent);
        mSharedPref.edit().putString(DEFAULT_RING_TONE_URI_KEY, uri.toString()).apply();
        String displayNameString = getRingtoneString(uri);
        mSharedPref.edit().putString(DEFAULT_RING_TONE_NAME_KEY, displayNameString)
                .apply();

        LogUtils.d(LogUtils.LOGTAG, "saveRingtoneUri: uri = " + uri
                + " displayNameString = " + displayNameString);

        if(oldRingTone == null){
            oldRingTone = Uri.parse("");
        }

        if (uri != oldRingTone) {
            ContentResolver resolver = mContext.getContentResolver();
            final int takeFlags = intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;

            if (DocumentsContract.isDocumentUri(mContext, oldRingTone)) {
                try {
                    resolver.releasePersistableUriPermission(oldRingTone, takeFlags);
                } catch (Exception ex) {
                    LogUtils.e(LogUtils.LOGTAG, "releasePersistableUriPermission exception : "+ ex);
                }
            }

            if (DocumentsContract.isDocumentUri(mContext, uri)) {
                // Check for the freshest data and take persist permission.
                try {
                    resolver.takePersistableUriPermission(uri, takeFlags);
                } catch (Exception ex) {
                    LogUtils.e(LogUtils.LOGTAG, "takePersistableUriPermission exception : "+ ex);
                }
            }

            // sendBroadcast to refresh Alarm's ringtone which is default
            sendRefreshBroadcast(uri);
        }

        if (uri.toString().equals(SETTING_SYSTEM_RINGTONE)) {
            LogUtils.d(LogUtils.LOGTAG, "saveRingtoneUri: remove SP DEFAULT_RING_TONE_URI_KEY");
            mSharedPref.edit().remove(DEFAULT_RING_TONE_URI_KEY).apply();
        }
    }

    private void sendRefreshBroadcast(Uri uri) {
        Intent uriIntent = new Intent();
        uriIntent.putExtra(OLD_RING_TONE_URI_STRING, oldRingTone.toString());
        uriIntent.putExtra(NEW_RING_TONE_URI_STRING, uri.toString());
        uriIntent.setAction(REFRESH_DEFAULT_RINGTONE_ACTION);
        mContext.sendBroadcast(uriIntent);
    }

    private String getRingtoneString(Uri uri) {
        if (Alarm.NO_RINGTONE_URI.equals(uri)) {
            mRingtone = mContext.getResources().getString(R.string.silent_ringtone_title);
        } else {
            mRingtone = DataModel.getDataModel().getAlarmRingtoneTitle(uri);
        }
        return mRingtone;
    }

    private Uri getRingtoneUri(Intent intent) {
        Uri uri;
        if (mSelectSource == AlarmClockFragment.SEL_SRC_RINGTONE) {
            uri = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        } else {
            uri = intent.getData();
        }

        if (uri == null) {
            uri = Alarm.NO_RINGTONE_URI;
        }
        return uri;
    }

}
