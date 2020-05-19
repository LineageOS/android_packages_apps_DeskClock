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
import android.view.View;
import android.view.ViewGroup;

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.viewpager.widget.ViewPager;

import com.android.deskclock.DeskClock;
import com.android.deskclock.R;
import com.android.deskclock.data.DataModel;
import com.android.deskclock.data.Timer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Exercise the user interface that shows current timers.
 */
@RunWith(AndroidJUnit4ClassRunner.class)
public class TimerItemFragmentTest {

    @Rule
    public ActivityTestRule<DeskClock> rule = new ActivityTestRule<>(DeskClock.class, true);

    @Test
    public void ensureTimerIsHeldSuccessfully_whenOneTimerIsRunning() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final TimerFragment timerFragment = new TimerFragment();
        rule.getActivity().getFragmentManager()
                .beginTransaction().add(timerFragment, null).commit();
        Runnable selectTabRunnable = () -> {
            timerFragment.selectTab();
            Timer timer = DataModel.getDataModel().addTimer(5000L, "", false);

            // Get the view held by the TimerFragment
            final View view = timerFragment.getView();
            assertNotNull(view);

            // Get the TimerPagerAdapter associated with this view
            ViewPager viewPager = (ViewPager) view.findViewById(R.id.vertical_view_pager);
            TimerPagerAdapter adapter = (TimerPagerAdapter) viewPager.getAdapter();
            ViewGroup viewGroup = view.findViewById(R.id.timer_view);

            // Retrieve the TimerItemFragment from the adapter
            TimerItemFragment timerItemFragment =
                    (TimerItemFragment) adapter.instantiateItem(viewGroup, 0);

            // Assert that the correct timer is set
            assertEquals(timerItemFragment.getTimer(), timer);
            DataModel.getDataModel().removeTimer(timer);
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(selectTabRunnable);
    }
}
