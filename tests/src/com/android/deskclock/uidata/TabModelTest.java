/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.deskclock.uidata;

import android.content.Context;
import android.preference.PreferenceManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

import static com.android.deskclock.uidata.UiDataModel.Tab.ALARMS;
import static com.android.deskclock.uidata.UiDataModel.Tab.CLOCKS;
import static com.android.deskclock.uidata.UiDataModel.Tab.STOPWATCH;
import static com.android.deskclock.uidata.UiDataModel.Tab.TIMERS;

import static org.junit.Assert.assertSame;

@RunWith(AndroidJUnit4ClassRunner.class)
public class TabModelTest {

    private Locale defaultLocale;
    private TabModel tabModel;

    @Test
    public void ltrTabLayoutIndex() {
        setUpTabModel(new Locale("en", "US"));
        assertSame(ALARMS, tabModel.getTabAt(0));
        assertSame(CLOCKS, tabModel.getTabAt(1));
        assertSame(TIMERS, tabModel.getTabAt(2));
        assertSame(STOPWATCH, tabModel.getTabAt(3));
        Locale.setDefault(defaultLocale);
    }

    @Test
    public void rtlTabLayoutIndex() {
        setUpTabModel(new Locale("ar", "EG"));
        assertSame(STOPWATCH, tabModel.getTabAt(0));
        assertSame(TIMERS, tabModel.getTabAt(1));
        assertSame(CLOCKS, tabModel.getTabAt(2));
        assertSame(ALARMS, tabModel.getTabAt(3));
        Locale.setDefault(defaultLocale);
    }

    private void setUpTabModel(Locale testLocale) {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(testLocale);
        final Context context = ApplicationProvider.getApplicationContext();
        tabModel = new TabModel(PreferenceManager.getDefaultSharedPreferences(context));
    }
}
