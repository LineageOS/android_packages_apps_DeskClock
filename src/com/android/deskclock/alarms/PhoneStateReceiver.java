package com.android.deskclock.alarms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.deskclock.LogUtils;
import com.android.deskclock.Utils;
import com.android.deskclock.provider.AlarmInstance;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Watch the phone state to clear any alarms that are waiting for a call to end
 */
public class PhoneStateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        LogUtils.v("PhoneStateReceiver received intent " + intent);

        if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
            TelephonyManager mTelephonyManager = (TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            if (mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                // New call state is idle, update state for any pending alarms
                SharedPreferences sp = Utils.getDESharedPreferences(context);
                Set<String> alarms = sp.getStringSet(AlarmStateManager.ALARM_PENDING_ALARM_KEY,
                        new HashSet<String>());
                if (alarms.size() <= 0) {
                    return; // no alarms to fire
                }

                Iterator<String> iterator = alarms.iterator();

                while (iterator.hasNext()) {
                    String flatAlarm = iterator.next();
                    if (TextUtils.isEmpty(flatAlarm)) {
                        LogUtils.e("Unable to un-flatten alarm for restore");
                        return;
                    }

                    String [] items = flatAlarm.split("\\|");
                    if (items.length < 2) {
                        LogUtils.e("Unable to un-flatten alarm for restore");
                        return;
                    }

                    Uri uri = Uri.parse(items[0]);
                    AlarmInstance instance = AlarmInstance.getInstance(
                            context.getContentResolver(), AlarmInstance.getId(uri));
                    int alarmState = Integer.parseInt(items[1]);

                    AlarmStateManager.setChangeAlarmState(context, instance, alarmState);
                    iterator.remove();
                }

                // Clear out the pending alarms
                sp.edit().remove(AlarmStateManager.ALARM_PENDING_ALARM_KEY).commit();
            }
        }
    }
}