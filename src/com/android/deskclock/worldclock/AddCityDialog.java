/* Copyright (c) 2016, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.deskclock.worldclock;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.AnimationDrawable;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.deskclock.LogUtils;
import com.android.deskclock.R;
import com.android.deskclock.Utils;
import com.android.deskclock.worldclock.CityAndTimeZoneLocator.OnCityAndTimeZoneLocatedCallback;
import com.android.deskclock.worldclock.CityAndTimeZoneLocator.TZ;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class AddCityDialog implements OnClickListener,
        OnItemSelectedListener, TextWatcher, LocationListener {

    private static final int HOURS_1 = 60 * 60000;
    private static final long GPS_TIMEOUT = 30000L;
    private static final String STATE_CITY_NAME = "city_name";
    private static final String STATE_CITY_TIMEZONE = "city_tz";
    private static final String ISGPSREQUESTING = "city_dialog";
    private int mCityDialogWidth = -1;
    private int mCityDialogHeight = -1;
    public static CityTimeZone[] mZones;

    static final int REQUEST_FINE_LOCATION_PERMISSIONS = 20;

    public interface OnCitySelected {
        public void onCitySelected(String city, String tz);
        public void onCancelCitySelection();
    }

    private final AsyncTask<Void, Void, Void> mTzLoadTask = new AsyncTask<Void, Void, Void>() {


        @Override
        protected Void doInBackground(Void... params) {
            List<CityTimeZone> zones = loadTimeZones();
            Collections.sort(zones);

            mZones = zones.toArray(new CityTimeZone[zones.size()]);
            mDefaultTimeZonePos = zones.indexOf(mDefaultTimeZone);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (!isCancelled()) {
                int id = mSavedTimeZonePos != -1 ? mSavedTimeZonePos : mDefaultTimeZonePos;
                setTimeZoneData(mZones, id, true);
                mLoadingTz = false;
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            checkGpsAvailability();
        }
    };
    private boolean mReceiverRegistered;

    private final Runnable mGpsTimeout = new Runnable() {
        @Override
        public void run() {
            Toast.makeText(mContext, R.string.cities_add_gps_not_available,
                    Toast.LENGTH_SHORT).show();
            mGpsRequesting = false;
            mCityName.setEnabled(true);
            mCityName.setText("");
            mTimeZones.setEnabled(true);
            stopGpsAnimation();
            mGps.setImageResource(R.drawable.ic_gps);
            checkSelectionStatus();
            try {
                mLocationMgr.removeUpdates(AddCityDialog.this);
            } catch (SecurityException e){
                LogUtils.d(LogUtils.LOGTAG, "Runnable mGpsTimeout:occur security exception");
            }

        }
    };

    public static class CityTimeZone implements Comparable<CityTimeZone> {
        String mId;
        int mSign;
        int mHours;
        int mMinutes;
        String mLabel;
        boolean mHasDst;

        @Override
        public String toString() {
            if (mId == null) {
                // Loading
                return mLabel;
            }
            return String.format("GMT%s%02d:%02d - %s",
                    (mSign == -1 ? "-" : "+"), mHours, mMinutes, mLabel);
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof CityTimeZone) {
                return compareTo((CityTimeZone) other) == 0;
            }
            return false;
        }

        @Override
        public int compareTo(CityTimeZone other) {
            long offset = getOffset();
            long otherOffset = other.getOffset();
            if (offset != otherOffset) {
                return offset < otherOffset ? -1 : 1;
            }
            if (mHasDst != other.mHasDst) {
                return mHasDst ? 1 : -1;
            }
            return mLabel.compareTo(other.mLabel);
        }

        private long getOffset() {
            return mSign * (mHours * HOURS_1 + mMinutes * 60000);
        }
    }

    private final Context mContext;
    private final Handler mHandler;
    private final OnCitySelected mListener;
    private final AlertDialog mDialog;
    private final View dlgView;
    private EditText mCityName;
    private final ImageButton mGps;
    private Button mButton;
    public static TimeZoneSpinner mTimeZones;

    private LocationManager mLocationMgr;
    private ConnectivityManager mConnectivityMgr;
    private CityAndTimeZoneLocator mLocator;
    private boolean mLoadingTz;
    public boolean mGpsRequesting;

    private int mDefaultTimeZonePos;
    private int mSavedTimeZonePos;
    private CityTimeZone mDefaultTimeZone;

    public AddCityDialog(Context context, LayoutInflater inflater, OnCitySelected listener) {
        mContext = context;
        mHandler = new Handler();
        mListener = listener;
        mDefaultTimeZonePos = 0;
        mDefaultTimeZone = null;
        mSavedTimeZonePos = -1;

        mLocationMgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mConnectivityMgr = (ConnectivityManager)context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        mGpsRequesting = false;
        mLoadingTz = true;

        // Initialize dialog
        dlgView = inflater.inflate(R.layout.city_add, null);
        mCityName = (EditText) dlgView.findViewById(R.id.add_city_name);
        mCityName.addTextChangedListener(this);
        mTimeZones = (TimeZoneSpinner) dlgView.findViewById(R.id.add_city_tz);
        CityTimeZone loading = new CityTimeZone();
        loading.mId = null;
        loading.mLabel = context.getString(R.string.cities_add_loading);
        setTimeZoneData(new CityTimeZone[]{ loading }, 0, false);
        mTimeZones.setEnabled(false);
        mTimeZones.setOnItemSelectedListener(this);
        mGps = (ImageButton)dlgView.findViewById(R.id.add_city_gps);
        mGps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.post(new Runnable() {
                    public void run() {
                        if (!mGpsRequesting) {
                            requestGpsLocation();
                        } else {
                            cancelRequestGpsLocation();
                        }
                    }
                });
            }
        });
        mGps.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(mContext, R.string.cities_add_city_gps_cd, Toast.LENGTH_SHORT).show();
                mGps.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                return true;
            }
        });

        checkGpsAvailability();
        try {
            mLocationMgr.addGpsStatusListener(new GpsStatus.Listener() {
                @Override
                public void onGpsStatusChanged(int event) {
                }
            });
        } catch (SecurityException e) {
            LogUtils.d(LogUtils.LOGTAG, "AddCityDialog:occur security exception");
        }

        // Create the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.cities_add_city_title);
        builder.setView(dlgView);
        builder.setPositiveButton(context.getString(android.R.string.ok), this);
        builder.setNegativeButton(context.getString(android.R.string.cancel), null);
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (mReceiverRegistered) {
                    mContext.unregisterReceiver(mReceiver);
                    mReceiverRegistered = false;
                }
                cancelRequestGpsLocation();
                if (mListener != null) {
                    mListener.onCancelCitySelection();
                }
            }
        });
        builder.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (mReceiverRegistered) {
                    mContext.unregisterReceiver(mReceiver);
                    mReceiverRegistered = false;
                }
                cancelRequestGpsLocation();
                if (mListener != null) {
                    mListener.onCancelCitySelection();
                }
            }
        });
        mDialog = builder.create();

        // Register broadcast listeners
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(mReceiver, filter);
        mReceiverRegistered = true;
        mCityDialogWidth = dlgView.getWidth();
        mCityDialogHeight = dlgView.getHeight();
    }

    public void setTimeZoneData(CityTimeZone[] data, int selected,
            boolean enabled) {
        ArrayAdapter<CityTimeZone> adapter = new ArrayAdapter<CityTimeZone>(
                mContext, android.R.layout.simple_spinner_item, data);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTimeZones.setAdapter(adapter);
        mTimeZones.setSelection(selected);
        mTimeZones.setEnabled(enabled);
        if (mButton != null) {
            checkSelectionStatus();
        }
    }

    private List<CityTimeZone> loadTimeZones() {
        ArrayList<CityTimeZone> timeZones = new ArrayList<CityTimeZone>();
        Resources res = mContext.getResources();
        final long date = Calendar.getInstance().getTimeInMillis();
        mDefaultTimeZone = buildCityTimeZone(TimeZone.getDefault().getID(), date);
        String[] ids = res.getStringArray(R.array.cities_tz);
        for (String id : ids) {
            CityTimeZone zone = buildCityTimeZone(id, date);
            if (!timeZones.contains(zone)) {
                timeZones.add(zone);
            }
        }
        return timeZones;
    }

    private CityTimeZone buildCityTimeZone(String id, long date) {
        return buildCityTimeZone(TimeZone.getTimeZone(id), date);
    }

    private CityTimeZone buildCityTimeZone(final TimeZone tz, long date) {
        final int offset = tz.getOffset(date);
        final int p = Math.abs(offset);
        final boolean inDst = tz.inDaylightTime(new Date(date));

        CityTimeZone timeZone = new CityTimeZone();
        timeZone.mId = tz.getID();
        timeZone.mLabel = tz.getDisplayName(inDst, TimeZone.LONG);
        timeZone.mSign = offset < 0 ? -1 : 1;
        timeZone.mHours = p / (HOURS_1);
        timeZone.mMinutes = (p / 60000) % 60;
        timeZone.mHasDst = tz.useDaylightTime();

        return timeZone;
    }

    private CityTimeZone toCityTimeZone(TZ info) {
        final long date = Calendar.getInstance().getTimeInMillis();
        final String id;

        if (Arrays.binarySearch(TimeZone.getAvailableIDs(), info.name) < 0) {
            int seconds = info.offset < 0 ? -info.offset : info.offset;
            int hours = seconds / 3600;
            int minutes = (seconds - (hours * 3600)) / 60;
            id = String.format("GMT%s%02d%02d", info.offset < 0 ? "-" : "+", hours, minutes);
        } else {
            id = info.name;
        }
        return buildCityTimeZone(id, date);
    }

    public static void setSelectItem(int itemId) {
        mTimeZones.setSelection(itemId);
    }

    private void checkSelectionStatus() {
        String name = mCityName.getText().toString().toLowerCase();
        String tz = null;
        if (mTimeZones.getSelectedItem() != null) {
            tz = mTimeZones.getSelectedItem().toString();
        }
        boolean enabled =
                !mLoadingTz &&
                mCityName.isEnabled() && !TextUtils.isEmpty(name) &&
                mTimeZones.isEnabled() && !TextUtils.isEmpty(tz);
        mButton.setEnabled(enabled);
        if (enabled) {
            mButton.setTextColor(mContext.getResources().getColor(
                    R.color.color_accent, null));
        } else {
            mButton.setTextColor(mContext.getResources().getColor(
                    R.color.dialog_positive_button_disable, null));
        }
    }

    private void checkGpsAvailability() {
        boolean gpsEnabled = mLocationMgr.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = mLocationMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        mGps.setEnabled(gpsEnabled || (networkEnabled && isNetworkStatusAvailable()));
        LogUtils.d(LogUtils.LOGTAG, "checkGpsAvailability: gpsEnabled = "
                + gpsEnabled + " networkEnabled = " + networkEnabled);

        //check & request gps permission when GPS enable, if gps disable, don't
        if(gpsEnabled || (networkEnabled && isNetworkStatusAvailable())) {
            if(!hasPermissionOfFineLocation(mContext)
                    && Utils.isMOrLater()) {
                LogUtils.d(LogUtils.LOGTAG, "checkGpsAvailability:request fine location permission");
                final String[] perms = {Manifest.permission.ACCESS_FINE_LOCATION};
                ((CitySelectionActivity) mContext).requestPermissions(perms,
                        REQUEST_FINE_LOCATION_PERMISSIONS);
            }
        }
    }

    private boolean isNetworkStatusAvailable() {
        NetworkInfo activeNetworkInfo = mConnectivityMgr.getActiveNetworkInfo();
        if (activeNetworkInfo != null) {
            return activeNetworkInfo.isAvailable();
        }
        return false;
    }

    private void requestGpsLocation() {
        Criteria criteria = new Criteria();
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_LOW);
        Looper looper = mContext.getMainLooper();
        mHandler.postDelayed(mGpsTimeout, GPS_TIMEOUT);
        mGpsRequesting = true;
        mCityName.setEnabled(false);
        mCityName.setText(R.string.cities_add_searching);
        mTimeZones.setEnabled(false);
        mGps.setImageResource(R.drawable.ic_gps_anim);
        try {
            ((AnimationDrawable)mGps.getDrawable()).start();
        } catch (Exception ex) {
            // Ignore
        }

        // We have to use the network to locate the city and tz, so user also the network to detect
        // location (we not need to much accuracy and this method is faster). Otherwise, use
        // the GPS to locate the coordinates
        try {
            if (mLocationMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocationMgr.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 5000, 0, this, looper);
            } else {
                mLocationMgr.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 5000, 0, this, looper);
            }
        } catch (SecurityException e) {
            LogUtils.d(LogUtils.LOGTAG, "requestGpsLocation:occur security exception");
        }
    }

    private void cancelRequestGpsLocation() {
        mHandler.removeCallbacks(mGpsTimeout);
        try {
            mLocationMgr.removeUpdates(this);
        } catch (SecurityException e){
            LogUtils.d(LogUtils.LOGTAG, "cancelRequestGpsLocation:occur security exception");
        }
        mGpsRequesting = false;
        mCityName.setText("");
        mCityName.setEnabled(true);
        mTimeZones.setEnabled(true);
        stopGpsAnimation();
        mGps.setImageResource(R.drawable.ic_gps);
    }

    private void stopGpsAnimation() {
        Drawable gpsDrawable = mGps.getDrawable();
        if (gpsDrawable instanceof AnimationDrawable) {
            ((AnimationDrawable) gpsDrawable).stop();
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        String name = mCityName.getText().toString().toUpperCase(Locale.getDefault());
        CityTimeZone ctz = null;
        if (mTimeZones.getSelectedItem() != null) {
            ctz = (CityTimeZone)mTimeZones.getSelectedItem();
        }
        if (ctz != null && mListener != null) {
            mListener.onCitySelected(name, ctz.mId);
        }
    }

    /* package */ void show() {
        mDialog.show();
        mTzLoadTask.execute();
        mButton = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        mButton.setEnabled(false);
    }

    /* package */ void dismiss() {
        mDialog.dismiss();
        if (mTzLoadTask.getStatus() == AsyncTask.Status.RUNNING) {
            mTzLoadTask.cancel(true);
        }
        if (mLocator != null) {
            mLocator.cancel();
        }
    }

    protected void onSaveInstanceState(Bundle outState) {
        String name = mCityName.getText().toString();
        int tz = -1;
        if (mTimeZones.getSelectedItem() != null) {
            tz = mTimeZones.getSelectedItemPosition();
        }
        outState.putBoolean(ISGPSREQUESTING, mGpsRequesting);
        outState.putString(STATE_CITY_NAME, name);
        outState.putInt(STATE_CITY_TIMEZONE, tz);
    }

    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        String name = savedInstanceState.getString(STATE_CITY_NAME);
        Boolean isGpsRequesting = savedInstanceState.getBoolean(ISGPSREQUESTING);
        if (name != null) {
            mCityName.setText(name);
        }
        if(isGpsRequesting){
            mCityName.setText("");
        }
        mSavedTimeZonePos = savedInstanceState.getInt(STATE_CITY_TIMEZONE, -1);
    }


    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        checkSelectionStatus();
    }

    public void onNothingSelected(AdapterView<?> parent) {
        checkSelectionStatus();
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void afterTextChanged(Editable s) {
        if (mButton != null) {
            checkSelectionStatus();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!mGpsRequesting) return;
        mGpsRequesting = false;
        mHandler.removeCallbacks(mGpsTimeout);
        try {
            mLocationMgr.removeUpdates(this);
        } catch (SecurityException e) {
            LogUtils.d(LogUtils.LOGTAG, "onLocationChanged:occur security exception");
        }
        CityAndTimeZoneLocator mLocator = new CityAndTimeZoneLocator(
                mContext, location, mConnectivityMgr, new OnCityAndTimeZoneLocatedCallback() {
            @Override
            @SuppressWarnings("unchecked")
            public void onCityAndTimeZoneLocated(String city, TZ timezone) {
                CityTimeZone ctz = toCityTimeZone(timezone);
                int pos = ((ArrayAdapter<CityTimeZone>)mTimeZones.getAdapter()).getPosition(ctz);
                if (pos == -1) {
                    // This mean you are in the middle of the ocean and Android doesn't have
                    // a timezone definition for you.
                    pos = mDefaultTimeZonePos;
                }

                // Update the views with the new information
                updateViews(city, pos);
            }

            @Override
            public void onNoCityAndTimeZoneLocateResults() {
                //No results
                Toast.makeText(mContext, R.string.cities_add_gps_no_results,
                        Toast.LENGTH_SHORT).show();
                updateViews("", -1);
            }

            @Override
            public void onCityAndTimeZoneLocateError() {
                // Not available
                Toast.makeText(mContext, R.string.cities_add_gps_not_available,
                        Toast.LENGTH_SHORT).show();
                updateViews("", -1);
            }

            private void updateViews(String city, int tz) {
                mCityName.setText(city);
                mCityName.setEnabled(true);
                if (tz != -1) {
                    mTimeZones.setSelection(tz);
                }
                mTimeZones.setEnabled(true);
                stopGpsAnimation();
                mGps.setImageResource(R.drawable.ic_gps);
                checkSelectionStatus();
            }
        });
        mLocator.resolve();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
        checkGpsAvailability();
    }

    @Override
    public void onProviderDisabled(String provider) {
        checkGpsAvailability();
    }


    public static boolean hasPermissionOfFineLocation(Context context) {
        final PackageManager pm = context.getPackageManager();
        final String packageName = context.getPackageName();

        // If the permission is already granted, return true.
        if (pm.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, packageName)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }
}
