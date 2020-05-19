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

import android.content.Context;
import android.content.Intent;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.rule.ActivityTestRule;

import com.android.deskclock.data.DataModel;
import com.android.deskclock.data.Timer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertSame;

@RunWith(AndroidJUnit4ClassRunner.class)
public class ExpiredTimersActivityTest {

    @Rule
    public ActivityTestRule<ExpiredTimersActivity> rule =
            new ActivityTestRule<>(ExpiredTimersActivity.class, true, false);

    @Test
    public void configurationChanges_DoNotResetFiringTimer() {
        // Construct an ExpiredTimersActivity to display the firing timer.
        final Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = new Intent()
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        final ExpiredTimersActivity activity = rule.launchActivity(intent);

        Runnable fireTimerRunnable = () -> {
            // Create a firing timer.
            final DataModel dm = DataModel.getDataModel();
            Timer timer = dm.addTimer(60000L, "", false);
            dm.startTimer(timer);
            dm.expireTimer(null, dm.getTimer(timer.getId()));
            timer = dm.getTimer(timer.getId());

            // Make the ExpiredTimersActivity believe it has been displayed to the user.
            activity.getWindow().getCallback().onWindowFocusChanged(true);

            // Simulate a configuration change by recreating the activity.
            activity.recreate();

            // Verify that the recreation did not alter the firing timer.
            assertSame(timer, dm.getTimer(timer.getId()));
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(fireTimerRunnable);
    }
}
