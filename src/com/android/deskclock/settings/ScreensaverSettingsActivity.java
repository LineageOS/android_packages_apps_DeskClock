/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.deskclock.settings;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.deskclock.R;
import com.android.deskclock.data.DataModel;

/**
 * Settings for Clock screen saver
 */
public final class ScreensaverSettingsActivity extends AppCompatActivity {

    public static final String KEY_CLOCK_STYLE = "screensaver_clock_style";
    public static final String KEY_NIGHT_MODE = "screensaver_night_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screensaver_settings);
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    public static class PrefsFragment extends PreferenceFragmentCompat
            implements Preference.OnPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getPreferenceManager().setStorageDeviceProtected();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.screensaver_settings);
        }

        @Override
        public void onResume() {
            super.onResume();
            refresh();
        }

        @Override
        public boolean onPreferenceChange(Preference pref, Object newValue) {
            if (KEY_CLOCK_STYLE.equals(pref.getKey())) {
                final ListPreference clockStylePref = (ListPreference) pref;
                final int index = clockStylePref.findIndexOfValue((String) newValue);
                clockStylePref.setSummary(clockStylePref.getEntries()[index]);
            }
            return true;
        }

        private void refresh() {
            final ListPreference clockStylePref = findPreference(KEY_CLOCK_STYLE);
            final CheckBoxPreference nightModePref = findPreference(KEY_NIGHT_MODE);
            if (clockStylePref != null && nightModePref != null) {
                final int index = clockStylePref.findIndexOfValue(DataModel.getDataModel().
                        getScreensaverClockStyle().toString().toLowerCase());
                clockStylePref.setValueIndex(index);
                clockStylePref.setSummary(clockStylePref.getEntries()[index]);
                clockStylePref.setOnPreferenceChangeListener(this);
                nightModePref.setChecked(DataModel.getDataModel().getScreensaverNightModeOn());
            }
        }
    }
}
