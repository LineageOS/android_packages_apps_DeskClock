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

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;

import static com.android.deskclock.uidata.PeriodicCallbackModel.Period.HOUR;
import static com.android.deskclock.uidata.PeriodicCallbackModel.Period.MIDNIGHT;
import static com.android.deskclock.uidata.PeriodicCallbackModel.Period.MINUTE;
import static com.android.deskclock.uidata.PeriodicCallbackModel.Period.QUARTER_HOUR;

import static java.util.Calendar.MILLISECOND;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4ClassRunner.class)
public class PeriodicCallbackModelTest {

    @Test
    public void getMinuteDelay() {
        assertEquals(1, PeriodicCallbackModel.getDelay(56999, MINUTE, -3000));
        assertEquals(60000, PeriodicCallbackModel.getDelay(57000, MINUTE, -3000));
        assertEquals(59999, PeriodicCallbackModel.getDelay(57001, MINUTE, -3000));

        assertEquals(1, PeriodicCallbackModel.getDelay(59999, MINUTE, 0));
        assertEquals(60000, PeriodicCallbackModel.getDelay(60000, MINUTE, 0));
        assertEquals(59999, PeriodicCallbackModel.getDelay(60001, MINUTE, 0));

        assertEquals(3001, PeriodicCallbackModel.getDelay(59999, MINUTE, 3000));
        assertEquals(3000, PeriodicCallbackModel.getDelay(60000, MINUTE, 3000));
        assertEquals(1, PeriodicCallbackModel.getDelay(62999, MINUTE, 3000));
        assertEquals(60000, PeriodicCallbackModel.getDelay(63000, MINUTE, 3000));
        assertEquals(59999, PeriodicCallbackModel.getDelay(63001, MINUTE, 3000));
    }

    @Test
    public void getQuarterHourDelay() {
        assertEquals(1, PeriodicCallbackModel.getDelay(896999, QUARTER_HOUR, -3000));
        assertEquals(900000, PeriodicCallbackModel.getDelay(897000, QUARTER_HOUR, -3000));
        assertEquals(899999, PeriodicCallbackModel.getDelay(897001, QUARTER_HOUR, -3000));

        assertEquals(1, PeriodicCallbackModel.getDelay(899999, QUARTER_HOUR, 0));
        assertEquals(900000, PeriodicCallbackModel.getDelay(900000, QUARTER_HOUR, 0));
        assertEquals(899999, PeriodicCallbackModel.getDelay(900001, QUARTER_HOUR, 0));

        assertEquals(3001, PeriodicCallbackModel.getDelay(899999, QUARTER_HOUR, 3000));
        assertEquals(3000, PeriodicCallbackModel.getDelay(900000, QUARTER_HOUR, 3000));
        assertEquals(1, PeriodicCallbackModel.getDelay(902999, QUARTER_HOUR, 3000));
        assertEquals(900000, PeriodicCallbackModel.getDelay(903000, QUARTER_HOUR, 3000));
        assertEquals(899999, PeriodicCallbackModel.getDelay(903001, QUARTER_HOUR, 3000));
    }

    @Test
    public void getHourDelay() {
        assertEquals(1, PeriodicCallbackModel.getDelay(3596999, HOUR, -3000));
        assertEquals(3600000, PeriodicCallbackModel.getDelay(3597000, HOUR, -3000));
        assertEquals(3599999, PeriodicCallbackModel.getDelay(3597001, HOUR, -3000));

        assertEquals(1, PeriodicCallbackModel.getDelay(3599999, HOUR, 0));
        assertEquals(3600000, PeriodicCallbackModel.getDelay(3600000, HOUR, 0));
        assertEquals(3599999, PeriodicCallbackModel.getDelay(3600001, HOUR, 0));

        assertEquals(3001, PeriodicCallbackModel.getDelay(3599999, HOUR, 3000));
        assertEquals(3000, PeriodicCallbackModel.getDelay(3600000, HOUR, 3000));
        assertEquals(1, PeriodicCallbackModel.getDelay(3602999, HOUR, 3000));
        assertEquals(3600000, PeriodicCallbackModel.getDelay(3603000, HOUR, 3000));
        assertEquals(3599999, PeriodicCallbackModel.getDelay(3603001, HOUR, 3000));
    }

    @Test
    public void getMidnightDelay() {
        final Calendar c = Calendar.getInstance();
        c.set(2016, 0, 20, 0, 0, 0);
        c.set(MILLISECOND, 0);
        final long now = c.getTimeInMillis();

        assertEquals(1, PeriodicCallbackModel.getDelay(now - 3001, MIDNIGHT, -3000));
        assertEquals(86400000, PeriodicCallbackModel.getDelay(now - 3000, MIDNIGHT, -3000));
        assertEquals(86399999, PeriodicCallbackModel.getDelay(now - 2999, MIDNIGHT, -3000));

        assertEquals(1, PeriodicCallbackModel.getDelay(now - 1, MIDNIGHT, 0));
        assertEquals(86400000, PeriodicCallbackModel.getDelay(now, MIDNIGHT, 0));
        assertEquals(86399999, PeriodicCallbackModel.getDelay(now + 1, MIDNIGHT, 0));

        assertEquals(3001, PeriodicCallbackModel.getDelay(now - 1, MIDNIGHT, 3000));
        assertEquals(3000, PeriodicCallbackModel.getDelay(now, MIDNIGHT, 3000));
        assertEquals(1, PeriodicCallbackModel.getDelay(now + 2999, MIDNIGHT, 3000));
        assertEquals(86400000, PeriodicCallbackModel.getDelay(now + 3000, MIDNIGHT, 3000));
        assertEquals(86399999, PeriodicCallbackModel.getDelay(now + 3001, MIDNIGHT, 3000));
    }
}
