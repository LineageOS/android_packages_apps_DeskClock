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
import androidx.recyclerview.widget.RecyclerView;

import com.android.deskclock.DeskClock;
import com.android.deskclock.R;
import com.android.deskclock.data.DataModel;
import com.android.deskclock.data.Timer;
import com.android.deskclock.uidata.UiDataModel;
import com.android.deskclock.widget.MockFabContainer;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4ClassRunner.class)
public class TimerFragmentTest {

    private TimerFragment fragment;
    private View timersView;
    private View timerSetupView;
    private RecyclerView recyclerView;
    private TimerAdapter adapter;

    private ImageView fab;
    private ImageView leftButton;
    private ImageView rightButton;

    @Rule
    public ActivityTestRule<DeskClock> rule = new ActivityTestRule<>(DeskClock.class, true);

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
            fragment = (TimerFragment) rule.getActivity().getSupportFragmentManager()
                    .findFragmentByTag(UiDataModel.Tab.TIMERS.name());
            if (fragment == null) {
                UiDataModel.getUiDataModel().setSelectedTab(UiDataModel.Tab.TIMERS);
                rule.getActivity().getSupportFragmentManager().executePendingTransactions();
                fragment = (TimerFragment) rule.getActivity().getSupportFragmentManager()
                        .findFragmentByTag(UiDataModel.Tab.TIMERS.name());
            }

            fragment.onStart();
            fragment.selectTab();
            final MockFabContainer fabContainer =
                    new MockFabContainer(fragment, ApplicationProvider.getApplicationContext());
            fragment.setFabContainer(fabContainer);

            final View view = fragment.getView();
            assertNotNull(view);

            timersView = view.findViewById(R.id.timer_view);
            timerSetupView = view.findViewById(R.id.timer_setup);
            recyclerView = view.findViewById(R.id.recycler_view);
            adapter = (TimerAdapter) recyclerView.getAdapter();

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
        recyclerView = null;
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
        final TimerItem timerItem = (TimerItem) recyclerView.findViewHolderForAdapterPosition(0).itemView;
        final TextView timeText = timerItem.findViewById(R.id.timer_time_text);
        assertStateEquals(Timer.State.RESET, 0);
        clickView(timeText);
        assertStateEquals(Timer.State.RUNNING, 0);
    }

    @Test
    public void timeClick_startsSecondTimer() {
        setUpTwoTimers();

        setCurrentItem(1);
        final TimerItem timerItem = (TimerItem) recyclerView.findViewHolderForAdapterPosition(1).itemView;
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
        final TimerItem timerItem = (TimerItem) recyclerView.findViewHolderForAdapterPosition(0).itemView;
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
        final TimerItem timerItem = (TimerItem) recyclerView.findViewHolderForAdapterPosition(1).itemView;
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
        final TimerItem timerItem = (TimerItem) recyclerView.findViewHolderForAdapterPosition(0).itemView;
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
        final TimerItem timerItem = (TimerItem) recyclerView.findViewHolderForAdapterPosition(1).itemView;
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
        final TimerItem timerItem = (TimerItem) recyclerView.findViewHolderForAdapterPosition(0).itemView;
        final Button addMinute = timerItem.findViewById(R.id.add_one_min);
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
        final TimerItem timerItem = (TimerItem) recyclerView.findViewHolderForAdapterPosition(1).itemView;
        final Button addMinute = timerItem.findViewById(R.id.add_one_min);
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
        final TimerItem timerItem = (TimerItem) recyclerView.findViewHolderForAdapterPosition(0).itemView;
        final View reset = timerItem.findViewById(R.id.reset);
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
        final TimerItem timerItem = (TimerItem) recyclerView.findViewHolderForAdapterPosition(1).itemView;
        final View reset = timerItem.findViewById(R.id.reset);
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
        final TimerItem timerItem = (TimerItem) recyclerView.findViewHolderForAdapterPosition(0).itemView;
        final TextView label = timerItem.findViewById(R.id.timer_label);
        assertStateEquals(Timer.State.RESET, 0);
        clickView(label);
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
        assertEquals(0, recyclerView.getLayoutManager().findViewByPosition(0) != null ? 0 : -1);

        final Intent intent =
                new Intent(ApplicationProvider.getApplicationContext(), TimerService.class)
                        .setAction(TimerService.ACTION_SHOW_TIMER)
                        .putExtra(TimerService.EXTRA_TIMER_ID, 0);
        rule.getActivity().setIntent(intent);
        restartFragment();

        assertEquals(View.VISIBLE, timersView.getVisibility());
        assertEquals(View.GONE, timerSetupView.getVisibility());
        // Since it's a list, the target timer should be visible.
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
            assertEquals(count, adapter.getItemCount());
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
            recyclerView.scrollToPosition(position);
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
