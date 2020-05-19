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

package com.android.deskclock.timer;

import android.content.ComponentName;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.deskclock.data.DataModel;
import com.android.deskclock.data.Timer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static android.app.Service.START_NOT_STICKY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4ClassRunner.class)
public class TimerServiceTest {

    private TimerService timerService;
    private DataModel dataModel;

    @Before
    public void setUp() {
        dataModel = DataModel.getDataModel();
        timerService = new TimerService();
        timerService.onCreate();
    }

    @After
    public void tearDown() {
        clearTimers();
        dataModel = null;
        timerService = null;
    }

    private void clearTimers() {
        Runnable clearTimersRunnable = () -> {
            final List<Timer> timers = new ArrayList<>(DataModel.getDataModel().getTimers());
            for (Timer timer : timers) {
                DataModel.getDataModel().removeTimer(timer);
            }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(clearTimersRunnable);
    }

    @Test
    public void verifyIntentsHonored_whileTimersFire() {
        Runnable testRunnable = () -> {
            Timer timer1 = dataModel.addTimer(60000L, null, false);
            Timer timer2 = dataModel.addTimer(60000L, null, false);
            dataModel.startTimer(timer1);
            dataModel.startTimer(timer2);
            timer1 = dataModel.getTimer(timer1.getId());
            timer2 = dataModel.getTimer(timer2.getId());

            // Expire the first timer.
            dataModel.expireTimer(null, timer1);

            // Have TimerService honor the Intent.
            assertEquals(START_NOT_STICKY,
                    timerService.onStartCommand(getTimerServiceIntent(), 0, 0));

            // Expire the second timer.
            dataModel.expireTimer(null, timer2);

            // Have TimerService honor the Intent which updates the firing timers.
            assertEquals(START_NOT_STICKY,
                    timerService.onStartCommand(getTimerServiceIntent(), 0, 1));

            // Reset timer 1.
            dataModel.resetTimer(dataModel.getTimer(timer1.getId()));

            // Have TimerService honor the Intent which updates the firing timers.
            assertEquals(START_NOT_STICKY,
                    timerService.onStartCommand(getTimerServiceIntent(), 0, 2));

            // Remove timer 2.
            dataModel.removeTimer(dataModel.getTimer(timer2.getId()));
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(testRunnable);
    }

    @Test
    public void verifyIntentsHonored_ifNoTimersAreExpired() {
        Runnable testRunnable = () -> {
            Timer timer = dataModel.addTimer(60000L, null, false);
            dataModel.startTimer(timer);
            timer = dataModel.getTimer(timer.getId());

            // Expire the timer.
            dataModel.expireTimer(null, timer);

            final Intent timerServiceIntent = getTimerServiceIntent();

            // Reset the timer before TimerService starts.
            dataModel.resetTimer(dataModel.getTimer(timer.getId()));

            // Have TimerService honor the Intent.
            assertEquals(START_NOT_STICKY, timerService.onStartCommand(timerServiceIntent, 0, 0));
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(testRunnable);
    }

    private Intent getTimerServiceIntent() {
        Intent serviceIntent = new Intent(ApplicationProvider.getApplicationContext(),
                TimerService.class);

        final ComponentName component = serviceIntent.getComponent();
        assertNotNull(component);
        assertEquals(TimerService.class.getName(), component.getClassName());

        return serviceIntent;
    }
}
