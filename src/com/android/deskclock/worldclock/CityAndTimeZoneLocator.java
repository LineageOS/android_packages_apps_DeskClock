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

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Locale;

public class CityAndTimeZoneLocator {

    private static final String TAG = "CityAndTimeZoneLocator";

    // Google Maps TimeZone Api: Allowed 2500 calls per day
    private static final String TIMEZONE_SERVICE_URI =
            "https://maps.googleapis.com/maps/api/timezone/xml?location=%s,%s&timestamp=%s&sensor=%s";

    private static final String TIMEZONE_ROOT = "TimeZoneResponse";
    private static final String TIMEZONE_STATUS_FLD = "status";
    private static final String TIMEZONE_RAW_OFFSET_FLD = "raw_offset";
    private static final String TIMEZONE_TZ_ID_FLD = "time_zone_id";

    private static final String TIMEZONE_VALID_STATUS = "OK";

    public static class TZ {
        public final String name;
        public final int offset;
        private TZ (String name, int offset) {
            this.name = name;
            this.offset = offset;
        }
    }

    public interface OnCityAndTimeZoneLocatedCallback {
        /**
         * When location success
         *
         * @param city The name of the city
         * @param timezone The timezone reference or null if no network available
         */
        public void onCityAndTimeZoneLocated(String city, TZ timezone);

        /**
         * When the location of the city or the timezone didn't return data
         */
        public void onNoCityAndTimeZoneLocateResults();

        /**
         * When the location of the city or the timezone failed
         */
        public void onCityAndTimeZoneLocateError();
    }

    private final AsyncTask<Void, Void, Void> mResolveTask = new AsyncTask<Void, Void, Void>() {

        private boolean mHasNetworkAvailable = false;
        private String mCity = null;
        private TZ mTZ = null;

        @Override
        protected Void doInBackground(Void... params) {
            // Resolve city
            mCity = resolveCity();
            if (mCity == null) {
                return null;
            }

            // Resolve timezone
            NetworkInfo activeNetworkInfo = mConnMgr.getActiveNetworkInfo();
            if (activeNetworkInfo != null && activeNetworkInfo.isAvailable()) {
                mHasNetworkAvailable = true;
                mTZ = resolveTimeZone();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (isCancelled()) {
                return;
            }
            if (mCity == null || (mHasNetworkAvailable && mTZ == null)) {
                if (mCallback != null) {
                    mCallback.onCityAndTimeZoneLocateError();
                }
                return;
            }
            mCallback.onCityAndTimeZoneLocated(mCity, mTZ);
        }
    };

    private final Context mContext;
    private ConnectivityManager mConnMgr;
    private final Location mLocation;
    private final OnCityAndTimeZoneLocatedCallback mCallback;

    public CityAndTimeZoneLocator(Context context, Location location,
            ConnectivityManager connMgr, OnCityAndTimeZoneLocatedCallback callback) {
        mContext = context;
        mLocation = location;
        mConnMgr = connMgr;
        mCallback = callback;
    }

    /**
     * This method resolve the city and its timezone and returns the information by
     * the <code>OnCityAndTimeZoneLocatedCallback</code> callback.
     */
    public void resolve() {
        mResolveTask.execute();
    }

    public void cancel() {
        if (mResolveTask.getStatus() == AsyncTask.Status.RUNNING) {
            mResolveTask.cancel(true);
        }
    }

    private String resolveCity() {
        try {
            Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());
            List<Address> addresses =
                    geocoder.getFromLocation(mLocation.getLatitude(), mLocation.getLongitude(), 1);
            if (addresses.size() > 0) {
                return addresses.get(0).getLocality();
            } else {
                Log.w(TAG, "No city data");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to retrieve city", e);
        }
        return null;
    }

    private TZ resolveTimeZone() {
        BufferedReader br = null;
        try {
            boolean gps = mLocation.getProvider().compareTo(
                    LocationManager.GPS_PROVIDER) == 0;
            final URI uri = new URI(String.format(TIMEZONE_SERVICE_URI,
                    String.valueOf(mLocation.getLatitude()),
                    String.valueOf(mLocation.getLongitude()),
                    String.valueOf(System.currentTimeMillis() / 1000L),
                    String.valueOf(gps)));
            URL url = new URL(uri.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                br = new BufferedReader(new InputStreamReader(
                        conn.getInputStream()));
                return parseTimeZoneResponse(br);
            }
        } catch (URISyntaxException e) {
            Log.wtf(TAG, "Failed constructing the timezone request URI", e);
        } catch (IOException e) {
            Log.e(TAG, "Failed to retrieve timezone", e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        return null;
    }

    private TZ parseTimeZoneResponse(BufferedReader br) {
        try {
            boolean status = false;
            String tzId = null;
            int rawOffset = 0;

            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(br);
            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, null, TIMEZONE_ROOT);

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String name = parser.getName();
                if (name.compareTo(TIMEZONE_STATUS_FLD) == 0) {
                    status = parser.nextText().compareTo(TIMEZONE_VALID_STATUS) == 0;
                } else if (status && name.compareTo(TIMEZONE_RAW_OFFSET_FLD) == 0 ) {
                    rawOffset = (int)Double.parseDouble(parser.nextText());
                } else if (status && name.compareTo(TIMEZONE_TZ_ID_FLD) == 0 ) {
                    tzId = parser.nextText();
                } else {
                    parser.nextText();
                }
            }

            // Have a valid response?
            if (status) {
                return new TZ(tzId, rawOffset);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to parse timezone response", e);
        } catch (XmlPullParserException e) {
            Log.e(TAG, "Failed to parse timezone response", e);
        }
        return null;
    }
}
