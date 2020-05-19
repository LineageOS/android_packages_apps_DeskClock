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
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.rule.ActivityTestRule;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.android.deskclock.DeskClock;
import com.android.deskclock.R;
import com.android.deskclock.data.DataModel;
import com.android.deskclock.data.Timer;
import com.android.deskclock.widget.MockFabContainer;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4ClassRunner.class)
public class TimerFragmentTest {

    private static int LIGHT;
    private static int DARK;
    private static int TOP;
    private static int BOTTOM;

    private static final int GONE = 0;

    private TimerFragment fragment;
    private View timersView;
    private View timerSetupView;
    private ViewPager viewPager;
    private TimerPagerAdapter adapter;

    private ImageView fab;
    private Button leftButton;
    private Button rightButton;

    @Rule
    public ActivityTestRule<DeskClock> rule = new ActivityTestRule<>(DeskClock.class, true);

    @BeforeClass
    public static void staticSetUp() {
        LIGHT = R.drawable.ic_swipe_circle_light;
        DARK = R.drawable.ic_swipe_circle_dark;
        TOP = R.drawable.ic_swipe_circle_top;
        BOTTOM = R.drawable.ic_swipe_circle_bottom;
    }

    private void setUpSingleTimer() {
        Runnable addTimerRunnable = () -> {
            DataModel.getDataModel().addTimer(60000L, null, false);
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(addTimerRunnable);
        setUpFragment();
    }

    private void setUpTwoTimers() {
        Runnable addTimerRunnable = () -> {
            DataModel.getDataModel().addTimer(60000L, null, false);
            DataModel.getDataModel().addTimer(90000L, null, false);
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(addTimerRunnable);
        setUpFragment();
    }

    private void setUpFragment() {
        Runnable setUpFragmentRunnable = () -> {
            ViewPager deskClockPager =
                    (ViewPager) rule.getActivity().findViewById(R.id.desk_clock_pager);
            PagerAdapter tabPagerAdapter = (PagerAdapter) deskClockPager.getAdapter();
            fragment = (TimerFragment) tabPagerAdapter.instantiateItem(deskClockPager, 2);
            fragment.onStart();
            fragment.selectTab();
            final MockFabContainer fabContainer =
                    new MockFabContainer(fragment, ApplicationProvider.getApplicationContext());
            fragment.setFabContainer(fabContainer);

            final View view = fragment.getView();
            assertNotNull(view);

            timersView = view.findViewById(R.id.timer_view);
            timerSetupView = view.findViewById(R.id.timer_setup);
            viewPager = view.findViewById(R.id.vertical_view_pager);
            adapter = (TimerPagerAdapter) viewPager.getAdapter();

            fab = fabContainer.getFab();
            leftButton = fabContainer.getLeftButton();
            rightButton = fabContainer.getRightButton();
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(setUpFragmentRunnable);
    }

    @After
    public void tearDown() {
        clearTimers();
        fragment = null;
        fab = null;
        timerSetupView = null;
        timersView = null;
        adapter = null;
        viewPager = null;
        leftButton = null;
        rightButton = null;
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
    public void initialStateNoTimers() {
        setUpFragment();
        assertEquals(View.VISIBLE, timerSetupView.getVisibility());
        assertEquals(View.GONE, timersView.getVisibility());
        assertAdapter(0);
    }

    @Test
    public void initialStateOneTimer() {
        setUpSingleTimer();
        assertEquals(View.VISIBLE, timersView.getVisibility());
        assertEquals(View.GONE, timerSetupView.getVisibility());
        assertAdapter(1);
    }

    @Test
    public void initialStateTwoTimers() {
        setUpTwoTimers();
        assertEquals(View.VISIBLE, timersView.getVisibility());
        assertEquals(View.GONE, timerSetupView.getVisibility());
        assertAdapter(2);
    }

    @Test
    public void timeClick_startsTimer() {
        setUpSingleTimer();

        setCurrentItem(0);
        final TimerItem timerItem = (TimerItem) viewPager.getChildAt(0);
        final TextView timeText = timerItem.findViewById(R.id.timer_time_text);
        assertStateEquals(Timer.State.RESET, 0);
        clickView(timeText);
        assertStateEquals(Timer.State.RUNNING, 0);
    }

    @Test
    public void timeClick_startsSecondTimer() {
        setUpTwoTimers();

        setCurrentItem(1);
        final TimerItem timerItem = (TimerItem) viewPager.getChildAt(1);
        final TextView timeText = timerItem.findViewById(R.id.timer_time_text);
        assertStateEquals(Timer.State.RESET, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickView(timeText);
        assertStateEquals(Timer.State.RUNNING, 1);
        assertStateEquals(Timer.State.RESET, 0);
    }

    @Test
    public void timeClick_pausesTimer() {
        setUpSingleTimer();

        setCurrentItem(0);
        final TimerItem timerItem = (TimerItem) viewPager.getChildAt(0);
        final TextView timeText = timerItem.findViewById(R.id.timer_time_text);
        assertStateEquals(Timer.State.RESET, 0);
        clickView(timeText);
        assertStateEquals(Timer.State.RUNNING, 0);
        clickView(timeText);
        assertStateEquals(Timer.State.PAUSED, 0);
    }

    @Test
    public void timeClick_pausesSecondTimer() {
        setUpTwoTimers();

        setCurrentItem(1);
        final TimerItem timerItem = (TimerItem) viewPager.getChildAt(1);
        final TextView timeText = timerItem.findViewById(R.id.timer_time_text);
        assertStateEquals(Timer.State.RESET, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickView(timeText);
        assertStateEquals(Timer.State.RUNNING, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickView(timeText);
        assertStateEquals(Timer.State.PAUSED, 1);
        assertStateEquals(Timer.State.RESET, 0);
    }

    @Test
    public void timeClick_restartsTimer() {
        setUpSingleTimer();

        setCurrentItem(0);
        final TimerItem timerItem = (TimerItem) viewPager.getChildAt(0);
        final TextView timeText = timerItem.findViewById(R.id.timer_time_text);
        assertStateEquals(Timer.State.RESET, 0);
        clickView(timeText);
        assertStateEquals(Timer.State.RUNNING, 0);
        clickView(timeText);
        assertStateEquals(Timer.State.PAUSED, 0);
        clickView(timeText);
        assertStateEquals(Timer.State.RUNNING, 0);
    }

    @Test
    public void timeClick_restartsSecondTimer() {
        setUpTwoTimers();

        setCurrentItem(1);
        final TimerItem timerItem = (TimerItem) viewPager.getChildAt(1);
        final TextView timeText = timerItem.findViewById(R.id.timer_time_text);
        assertStateEquals(Timer.State.RESET, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickView(timeText);
        assertStateEquals(Timer.State.RUNNING, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickView(timeText);
        assertStateEquals(Timer.State.PAUSED, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickView(timeText);
        assertStateEquals(Timer.State.RUNNING, 1);
        assertStateEquals(Timer.State.RESET, 0);
    }

    @Test
    public void fabClick_startsTimer() {
        setUpSingleTimer();

        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 0);
    }

    @Test
    public void fabClick_startsSecondTimer() {
        setUpTwoTimers();

        setCurrentItem(1);
        assertStateEquals(Timer.State.RESET, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 1);
        assertStateEquals(Timer.State.RESET, 0);
    }

    @Test
    public void fabClick_pausesTimer() {
        setUpSingleTimer();

        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 0);
        clickFab();
        assertStateEquals(Timer.State.PAUSED, 0);
    }

    @Test
    public void fabClick_pausesSecondTimer() {
        setUpTwoTimers();

        setCurrentItem(1);
        assertStateEquals(Timer.State.RESET, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.PAUSED, 1);
        assertStateEquals(Timer.State.RESET, 0);
    }

    @Test
    public void fabClick_restartsTimer() {
        setUpSingleTimer();

        setCurrentItem(0);
        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 0);
        clickFab();
        assertStateEquals(Timer.State.PAUSED, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 0);
    }

    @Test
    public void fabClick_restartsSecondTimer() {
        setUpTwoTimers();

        setCurrentItem(1);
        assertStateEquals(Timer.State.RESET, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.PAUSED, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 1);
        assertStateEquals(Timer.State.RESET, 0);
    }

    @Test
    public void fabClick_resetsTimer() {
        setUpSingleTimer();

        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 0);
        final Context context = fab.getContext();
        Runnable expireTimerRunnable = () -> {
            DataModel.getDataModel().expireTimer(null, DataModel.getDataModel().getTimers().get(0));
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(expireTimerRunnable);
        clickFab();
        assertStateEquals(Timer.State.RESET, 0);
    }

    @Test
    public void fabClick_resetsSecondTimer() {
        setUpTwoTimers();

        setCurrentItem(1);
        assertStateEquals(Timer.State.RESET, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 1);
        assertStateEquals(Timer.State.RESET, 0);
        final Context context = fab.getContext();
        Runnable expireTimerRunnable = () -> {
            DataModel.getDataModel().expireTimer(null, DataModel.getDataModel().getTimers().get(1));
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(expireTimerRunnable);
        clickFab();
        assertStateEquals(Timer.State.RESET, 1);
        assertStateEquals(Timer.State.RESET, 0);
    }

    @Test
    public void clickAdd_addsOneMinuteToTimer() {
        setUpSingleTimer();

        setCurrentItem(0);
        final TimerItem timerItem = (TimerItem) viewPager.getChildAt(0);
        final Button addMinute = timerItem.findViewById(R.id.reset_add);
        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 0);
        Runnable getTimersRunnable = () -> {
            long remainingTime1 = DataModel.getDataModel().getTimers().get(0).getRemainingTime();
            addMinute.performClick();
            long remainingTime2 = DataModel.getDataModel().getTimers().get(0).getRemainingTime();
            assertSame(Timer.State.RUNNING, DataModel.getDataModel().getTimers().get(0).getState());
            long expectedSeconds =
                    TimeUnit.MILLISECONDS.toSeconds(remainingTime1 + DateUtils.MINUTE_IN_MILLIS);
            long observedSeconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime2);
            assertEquals(expectedSeconds, observedSeconds);
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(getTimersRunnable);
    }

    @Test
    public void clickAdd_addsOneMinuteToSecondTimer() {
        setUpTwoTimers();

        setCurrentItem(1);
        final TimerItem timerItem = (TimerItem) viewPager.getChildAt(1);
        final Button addMinute = timerItem.findViewById(R.id.reset_add);
        assertStateEquals(Timer.State.RESET, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 1);
        assertStateEquals(Timer.State.RESET, 0);
        Runnable getTimersRunnable = () -> {
            long remainingTime1 = DataModel.getDataModel().getTimers().get(1).getRemainingTime();
            addMinute.performClick();
            long remainingTime2 = DataModel.getDataModel().getTimers().get(1).getRemainingTime();
            assertSame(Timer.State.RUNNING, DataModel.getDataModel().getTimers().get(1).getState());
            assertSame(Timer.State.RESET, DataModel.getDataModel().getTimers().get(0).getState());
            long expectedSeconds =
                    TimeUnit.MILLISECONDS.toSeconds(remainingTime1 + DateUtils.MINUTE_IN_MILLIS);
            long observedSeconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime2);
            assertEquals(expectedSeconds, observedSeconds);
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(getTimersRunnable);
    }

    @Test
    public void clickReset_resetsTimer() {
        setUpSingleTimer();

        setCurrentItem(0);
        final TimerItem timerItem = (TimerItem) viewPager.getChildAt(0);
        final Button reset = timerItem.findViewById(R.id.reset_add);
        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 0);
        clickFab();
        assertStateEquals(Timer.State.PAUSED, 0);
        clickView(reset);
        assertStateEquals(Timer.State.RESET, 0);
    }

    @Test
    public void clickReset_resetsSecondTimer() {
        setUpTwoTimers();

        setCurrentItem(1);
        final TimerItem timerItem = (TimerItem) viewPager.getChildAt(1);
        final Button reset = timerItem.findViewById(R.id.reset_add);
        assertStateEquals(Timer.State.RESET, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.RUNNING, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickFab();
        assertStateEquals(Timer.State.PAUSED, 1);
        assertStateEquals(Timer.State.RESET, 0);
        clickView(reset);
        assertStateEquals(Timer.State.RESET, 1);
        assertStateEquals(Timer.State.RESET, 0);
    }

    @Test
    public void labelClick_opensLabel() {
        setUpSingleTimer();

        setCurrentItem(0);
        final TimerItem timerItem = (TimerItem) viewPager.getChildAt(0);
        final TextView label = timerItem.findViewById(R.id.timer_label);
        assertStateEquals(Timer.State.RESET, 0);
        clickView(label);
    }

    //
    // 3 Indicators
    //

    @Test
    public void verify3Indicators0Pages() {
        assertIndicatorsEquals(0, 3, 0, GONE, GONE, GONE);
    }

    @Test
    public void verify3Indicators1Page() {
        assertIndicatorsEquals(0, 3, 1, GONE, GONE, GONE);
    }

    @Test
    public void verify3Indicators2Pages() {
        assertIndicatorsEquals(0, 3, 2, LIGHT, DARK, GONE);
        assertIndicatorsEquals(1, 3, 2, DARK, LIGHT, GONE);
    }

    @Test
    public void verify3Indicators3Pages() {
        assertIndicatorsEquals(0, 3, 3, LIGHT, DARK, DARK);
        assertIndicatorsEquals(1, 3, 3, DARK, LIGHT, DARK);
        assertIndicatorsEquals(2, 3, 3, DARK, DARK, LIGHT);
    }

    @Test
    public void verify3Indicators4Pages() {
        assertIndicatorsEquals(0, 3, 4, LIGHT, DARK, BOTTOM);
        assertIndicatorsEquals(1, 3, 4, DARK, LIGHT, BOTTOM);
        assertIndicatorsEquals(2, 3, 4, TOP, LIGHT, DARK);
        assertIndicatorsEquals(3, 3, 4, TOP, DARK, LIGHT);
    }

    @Test
    public void verify3Indicators5Pages() {
        assertIndicatorsEquals(0, 3, 5, LIGHT, DARK, BOTTOM);
        assertIndicatorsEquals(1, 3, 5, DARK, LIGHT, BOTTOM);
        assertIndicatorsEquals(2, 3, 5, TOP, LIGHT, BOTTOM);
        assertIndicatorsEquals(3, 3, 5, TOP, LIGHT, DARK);
        assertIndicatorsEquals(4, 3, 5, TOP, DARK, LIGHT);
    }

    @Test
    public void verify3Indicators6Pages() {
        assertIndicatorsEquals(0, 3, 6, LIGHT, DARK, BOTTOM);
        assertIndicatorsEquals(1, 3, 6, DARK, LIGHT, BOTTOM);
        assertIndicatorsEquals(2, 3, 6, TOP, LIGHT, BOTTOM);
        assertIndicatorsEquals(3, 3, 6, TOP, LIGHT, BOTTOM);
        assertIndicatorsEquals(4, 3, 6, TOP, LIGHT, DARK);
        assertIndicatorsEquals(5, 3, 6, TOP, DARK, LIGHT);
    }

    @Test
    public void verify3Indicators7Pages() {
        assertIndicatorsEquals(0, 3, 7, LIGHT, DARK, BOTTOM);
        assertIndicatorsEquals(1, 3, 7, DARK, LIGHT, BOTTOM);
        assertIndicatorsEquals(2, 3, 7, TOP, LIGHT, BOTTOM);
        assertIndicatorsEquals(3, 3, 7, TOP, LIGHT, BOTTOM);
        assertIndicatorsEquals(4, 3, 7, TOP, LIGHT, BOTTOM);
        assertIndicatorsEquals(5, 3, 7, TOP, LIGHT, DARK);
        assertIndicatorsEquals(6, 3, 7, TOP, DARK, LIGHT);
    }

    //
    // 4 Indicators
    //

    @Test
    public void verify4Indicators0Pages() {
        assertIndicatorsEquals(0, 4, 0, GONE, GONE, GONE, GONE);
    }

    @Test
    public void verify4Indicators1Page() {
        assertIndicatorsEquals(0, 4, 1, GONE, GONE, GONE, GONE);
    }

    @Test
    public void verify4Indicators2Pages() {
        assertIndicatorsEquals(0, 4, 2, LIGHT, DARK, GONE, GONE);
        assertIndicatorsEquals(1, 4, 2, DARK, LIGHT, GONE, GONE);
    }

    @Test
    public void verify4Indicators3Pages() {
        assertIndicatorsEquals(0, 4, 3, LIGHT, DARK, DARK, GONE);
        assertIndicatorsEquals(1, 4, 3, DARK, LIGHT, DARK, GONE);
        assertIndicatorsEquals(2, 4, 3, DARK, DARK, LIGHT, GONE);
    }

    @Test
    public void verify4Indicators4Pages() {
        assertIndicatorsEquals(0, 4, 4, LIGHT, DARK, DARK, DARK);
        assertIndicatorsEquals(1, 4, 4, DARK, LIGHT, DARK, DARK);
        assertIndicatorsEquals(2, 4, 4, DARK, DARK, LIGHT, DARK);
        assertIndicatorsEquals(3, 4, 4, DARK, DARK, DARK, LIGHT);
    }

    @Test
    public void verify4Indicators5Pages() {
        assertIndicatorsEquals(0, 4, 5, LIGHT, DARK, DARK, BOTTOM);
        assertIndicatorsEquals(1, 4, 5, DARK, LIGHT, DARK, BOTTOM);
        assertIndicatorsEquals(2, 4, 5, DARK, DARK, LIGHT, BOTTOM);
        assertIndicatorsEquals(3, 4, 5, TOP, DARK, LIGHT, DARK);
        assertIndicatorsEquals(4, 4, 5, TOP, DARK, DARK, LIGHT);
    }

    @Test
    public void verify4Indicators6Pages() {
        assertIndicatorsEquals(0, 4, 6, LIGHT, DARK, DARK, BOTTOM);
        assertIndicatorsEquals(1, 4, 6, DARK, LIGHT, DARK, BOTTOM);
        assertIndicatorsEquals(2, 4, 6, DARK, DARK, LIGHT, BOTTOM);
        assertIndicatorsEquals(3, 4, 6, TOP, DARK, LIGHT, BOTTOM);
        assertIndicatorsEquals(4, 4, 6, TOP, DARK, LIGHT, DARK);
        assertIndicatorsEquals(5, 4, 6, TOP, DARK, DARK, LIGHT);
    }

    @Test
    public void verify4Indicators7Pages() {
        assertIndicatorsEquals(0, 4, 7, LIGHT, DARK, DARK, BOTTOM);
        assertIndicatorsEquals(1, 4, 7, DARK, LIGHT, DARK, BOTTOM);
        assertIndicatorsEquals(2, 4, 7, DARK, DARK, LIGHT, BOTTOM);
        assertIndicatorsEquals(3, 4, 7, TOP, DARK, LIGHT, BOTTOM);
        assertIndicatorsEquals(4, 4, 7, TOP, DARK, LIGHT, BOTTOM);
        assertIndicatorsEquals(5, 4, 7, TOP, DARK, LIGHT, DARK);
        assertIndicatorsEquals(6, 4, 7, TOP, DARK, DARK, LIGHT);
    }

    @Test
    public void showTimerSetupView_fromIntent() {
        setUpSingleTimer();

        assertEquals(View.VISIBLE, timersView.getVisibility());
        assertEquals(View.GONE, timerSetupView.getVisibility());

        final Intent intent = TimerFragment.createTimerSetupIntent(fragment.getContext());
        rule.getActivity().setIntent(intent);
        restartFragment();

        assertEquals(View.GONE, timersView.getVisibility());
        assertEquals(View.VISIBLE, timerSetupView.getVisibility());
    }

    @Test
    public void showTimerSetupView_usesLabel_fromIntent() {
        setUpSingleTimer();

        assertEquals(View.VISIBLE, timersView.getVisibility());
        assertEquals(View.GONE, timerSetupView.getVisibility());

        final Intent intent = TimerFragment.createTimerSetupIntent(fragment.getContext());
        rule.getActivity().setIntent(intent);
        restartFragment();

        assertEquals(View.GONE, timersView.getVisibility());
        assertEquals(View.VISIBLE, timerSetupView.getVisibility());
        clickView(timerSetupView.findViewById(R.id.timer_setup_digit_3));

        clickFab();
    }

    @Test
    public void showTimer_fromIntent() {
        setUpTwoTimers();

        assertEquals(View.VISIBLE, timersView.getVisibility());
        assertEquals(View.GONE, timerSetupView.getVisibility());
        assertEquals(0, viewPager.getCurrentItem());

        final Intent intent =
                new Intent(ApplicationProvider.getApplicationContext(), TimerService.class)
                        .setAction(TimerService.ACTION_SHOW_TIMER)
                        .putExtra(TimerService.EXTRA_TIMER_ID, 0);
        rule.getActivity().setIntent(intent);
        restartFragment();

        assertEquals(View.VISIBLE, timersView.getVisibility());
        assertEquals(View.GONE, timerSetupView.getVisibility());
        assertEquals(1, viewPager.getCurrentItem());
    }

    private void assertIndicatorsEquals(
            int page, int indicatorCount, int pageCount, int... expected) {
        int[] actual = TimerFragment.computePageIndicatorStates(page, indicatorCount, pageCount);
        if (!Arrays.equals(expected, actual)) {
            final String expectedString = Arrays.toString(expected);
            final String actualString = Arrays.toString(actual);
            fail(String.format("Expected %s, found %s", expectedString, actualString));
        }
    }

    private void assertStateEquals(Timer.State expectedState, int index) {
        Runnable timerRunnable = () -> {
            final Timer.State actualState =
                    DataModel.getDataModel().getTimers().get(index).getState();
            assertSame(expectedState, actualState);
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(timerRunnable);
    }

    private void assertAdapter(int count) {
        Runnable assertRunnable = () -> {
            assertEquals(count, adapter.getCount());
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(assertRunnable);
    }

    private void restartFragment() {
        Runnable onStartRunnable = () -> {
            fragment.onStart();
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(onStartRunnable);
    }

    private void setCurrentItem(int position) {
        Runnable setCurrentItemRunnable = () -> {
            viewPager.setCurrentItem(position);
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(setCurrentItemRunnable);
    }

    private void clickView(View view) {
        Runnable clickRunnable = () -> {
            view.performClick();
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(clickRunnable);
    }

    private void clickFab() {
        Runnable clickRunnable = () -> {
            fab.performClick();
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(clickRunnable);
    }
}
