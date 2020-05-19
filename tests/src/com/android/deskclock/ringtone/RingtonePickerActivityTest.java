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

package com.android.deskclock.ringtone;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.InstrumentationRegistry;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.rule.ActivityTestRule;

import com.android.deskclock.ItemAdapter;
import com.android.deskclock.ItemAdapter.ItemHolder;
import com.android.deskclock.R;
import com.android.deskclock.Utils;
import com.android.deskclock.data.DataModel;
import com.android.deskclock.provider.Alarm;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Exercise the user interface that adjusts the selected ringtone.
 */
@RunWith(AndroidJUnit4ClassRunner.class)
public class RingtonePickerActivityTest {

    private RingtonePickerActivity activity;
    private RecyclerView ringtoneList;
    private ItemAdapter<ItemHolder<Uri>> ringtoneAdapter;

    public static final Uri ALERT = Uri.parse("content://settings/system/alarm_alert");
    public static final Uri CUSTOM_RINGTONE_1 = Uri.parse("content://media/external/audio/one.ogg");

    @Rule
    public ActivityTestRule<RingtonePickerActivity> rule =
            new ActivityTestRule<>(RingtonePickerActivity.class, true, false);

    @Before
    @After
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
    }

    @Test
    public void validateDefaultState_TimerRingtonePicker() {
        createTimerRingtonePickerActivity();

        final List<ItemHolder<Uri>> systemRingtoneHolders = ringtoneAdapter.getItems();

        assertEquals(28, systemRingtoneHolders.size());
        final Iterator<ItemHolder<Uri>> itemsIter = systemRingtoneHolders.iterator();

        final HeaderHolder filesHeaderHolder = (HeaderHolder) itemsIter.next();
        assertEquals(R.string.your_sounds, filesHeaderHolder.getTextResId());
        assertEquals(HeaderViewHolder.VIEW_TYPE_ITEM_HEADER, filesHeaderHolder.getItemViewType());

        final AddCustomRingtoneHolder addNewHolder = (AddCustomRingtoneHolder) itemsIter.next();
        assertEquals(AddCustomRingtoneViewHolder.VIEW_TYPE_ADD_NEW, addNewHolder.getItemViewType());

        final HeaderHolder systemHeaderHolder = (HeaderHolder) itemsIter.next();
        assertEquals(R.string.device_sounds, systemHeaderHolder.getTextResId());
        assertEquals(HeaderViewHolder.VIEW_TYPE_ITEM_HEADER, systemHeaderHolder.getItemViewType());

        final RingtoneHolder silentHolder = (RingtoneHolder) itemsIter.next();
        assertEquals(Utils.RINGTONE_SILENT, silentHolder.getUri());
        assertEquals(RingtoneViewHolder.VIEW_TYPE_SYSTEM_SOUND, silentHolder.getItemViewType());

        final RingtoneHolder defaultHolder = (RingtoneHolder) itemsIter.next();
        assertEquals(RingtoneViewHolder.VIEW_TYPE_SYSTEM_SOUND, defaultHolder.getItemViewType());

        Runnable assertRunnable = () -> {
            assertEquals("Silent", silentHolder.getName());
            assertEquals("Timer Expired", defaultHolder.getName());
            assertEquals(DataModel.getDataModel().getDefaultTimerRingtoneUri(),
                    defaultHolder.getUri());
            // Verify initial selection.
            assertEquals(
                    DataModel.getDataModel().getTimerRingtoneUri(),
                    DataModel.getDataModel().getDefaultTimerRingtoneUri());
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(assertRunnable);
    }

    @Test
    public void validateDefaultState_AlarmRingtonePicker() {
        createAlarmRingtonePickerActivity(ALERT);

        final List<ItemHolder<Uri>> systemRingtoneHolders = ringtoneAdapter.getItems();

        assertEquals(28, systemRingtoneHolders.size());
        final Iterator<ItemHolder<Uri>> itemsIter = systemRingtoneHolders.iterator();

        final HeaderHolder filesHeaderHolder = (HeaderHolder) itemsIter.next();
        assertEquals(R.string.your_sounds, filesHeaderHolder.getTextResId());
        assertEquals(HeaderViewHolder.VIEW_TYPE_ITEM_HEADER, filesHeaderHolder.getItemViewType());

        final AddCustomRingtoneHolder addNewHolder = (AddCustomRingtoneHolder) itemsIter.next();
        assertEquals(AddCustomRingtoneViewHolder.VIEW_TYPE_ADD_NEW, addNewHolder.getItemViewType());

        final HeaderHolder systemHeaderHolder = (HeaderHolder) itemsIter.next();
        assertEquals(R.string.device_sounds, systemHeaderHolder.getTextResId());
        assertEquals(HeaderViewHolder.VIEW_TYPE_ITEM_HEADER, systemHeaderHolder.getItemViewType());

        final RingtoneHolder silentHolder = (RingtoneHolder) itemsIter.next();
        assertEquals(Utils.RINGTONE_SILENT, silentHolder.getUri());
        assertEquals(RingtoneViewHolder.VIEW_TYPE_SYSTEM_SOUND, silentHolder.getItemViewType());

        final RingtoneHolder defaultHolder = (RingtoneHolder) itemsIter.next();
        assertEquals(RingtoneViewHolder.VIEW_TYPE_SYSTEM_SOUND, defaultHolder.getItemViewType());

        Runnable assertRunnable = () -> {
            assertEquals("Silent", silentHolder.getName());
            assertEquals("Default alarm sound", defaultHolder.getName());
            assertEquals(DataModel.getDataModel().getDefaultAlarmRingtoneUri(),
                    defaultHolder.getUri());
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(assertRunnable);
    }

    @Test
    public void validateDefaultState_TimerRingtonePicker_WithCustomRingtones() {
        Runnable customRingtoneRunnable = () -> {
            DataModel.getDataModel().addCustomRingtone(CUSTOM_RINGTONE_1, "CustomSound");
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(customRingtoneRunnable);
        createTimerRingtonePickerActivity();

        final List<ItemHolder<Uri>> systemRingtoneHolders = ringtoneAdapter.getItems();

        assertEquals(29, systemRingtoneHolders.size());
        final Iterator<ItemHolder<Uri>> itemsIter = systemRingtoneHolders.iterator();

        final HeaderHolder filesHeaderHolder = (HeaderHolder) itemsIter.next();
        assertEquals(R.string.your_sounds, filesHeaderHolder.getTextResId());
        assertEquals(HeaderViewHolder.VIEW_TYPE_ITEM_HEADER, filesHeaderHolder.getItemViewType());

        final CustomRingtoneHolder customRingtoneHolder = (CustomRingtoneHolder) itemsIter.next();
        assertEquals("CustomSound", customRingtoneHolder.getName());
        assertEquals(CUSTOM_RINGTONE_1, customRingtoneHolder.getUri());
        assertEquals(RingtoneViewHolder.VIEW_TYPE_CUSTOM_SOUND,
                customRingtoneHolder.getItemViewType());

        final AddCustomRingtoneHolder addNewHolder = (AddCustomRingtoneHolder) itemsIter.next();
        assertEquals(AddCustomRingtoneViewHolder.VIEW_TYPE_ADD_NEW, addNewHolder.getItemViewType());

        final HeaderHolder systemHeaderHolder = (HeaderHolder) itemsIter.next();
        assertEquals(R.string.device_sounds, systemHeaderHolder.getTextResId());
        assertEquals(HeaderViewHolder.VIEW_TYPE_ITEM_HEADER, systemHeaderHolder.getItemViewType());

        final RingtoneHolder silentHolder = (RingtoneHolder) itemsIter.next();
        assertEquals(Utils.RINGTONE_SILENT, silentHolder.getUri());
        assertEquals(RingtoneViewHolder.VIEW_TYPE_SYSTEM_SOUND, silentHolder.getItemViewType());

        final RingtoneHolder defaultHolder = (RingtoneHolder) itemsIter.next();
        assertEquals(RingtoneViewHolder.VIEW_TYPE_SYSTEM_SOUND, defaultHolder.getItemViewType());

        Runnable assertRunnable = () -> {
            assertEquals("Silent", silentHolder.getName());
            assertEquals("Timer Expired", defaultHolder.getName());
            assertEquals(DataModel.getDataModel().getDefaultTimerRingtoneUri(),
                    defaultHolder.getUri());
            // Verify initial selection.
            assertEquals(
                    DataModel.getDataModel().getTimerRingtoneUri(),
                    DataModel.getDataModel().getDefaultTimerRingtoneUri());
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(assertRunnable);

        Runnable removeCustomRingtoneRunnable = () -> {
            DataModel.getDataModel().removeCustomRingtone(CUSTOM_RINGTONE_1);
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(removeCustomRingtoneRunnable);
    }

    @Test
    public void validateDefaultState_AlarmRingtonePicker_WithCustomRingtones() {
        Runnable customRingtoneRunnable = () -> {
            DataModel.getDataModel().addCustomRingtone(CUSTOM_RINGTONE_1, "CustomSound");
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(customRingtoneRunnable);
        createAlarmRingtonePickerActivity(ALERT);

        final List<ItemHolder<Uri>> systemRingtoneHolders = ringtoneAdapter.getItems();

        assertEquals(29, systemRingtoneHolders.size());
        final Iterator<ItemHolder<Uri>> itemsIter = systemRingtoneHolders.iterator();

        final HeaderHolder filesHeaderHolder = (HeaderHolder) itemsIter.next();
        assertEquals(R.string.your_sounds, filesHeaderHolder.getTextResId());
        assertEquals(HeaderViewHolder.VIEW_TYPE_ITEM_HEADER, filesHeaderHolder.getItemViewType());

        final CustomRingtoneHolder customRingtoneHolder = (CustomRingtoneHolder) itemsIter.next();
        assertEquals("CustomSound", customRingtoneHolder.getName());
        assertEquals(CUSTOM_RINGTONE_1, customRingtoneHolder.getUri());
        assertEquals(RingtoneViewHolder.VIEW_TYPE_CUSTOM_SOUND,
                customRingtoneHolder.getItemViewType());

        final AddCustomRingtoneHolder addNewHolder = (AddCustomRingtoneHolder) itemsIter.next();
        assertEquals(AddCustomRingtoneViewHolder.VIEW_TYPE_ADD_NEW, addNewHolder.getItemViewType());

        final HeaderHolder systemHeaderHolder = (HeaderHolder) itemsIter.next();
        assertEquals(R.string.device_sounds, systemHeaderHolder.getTextResId());
        assertEquals(HeaderViewHolder.VIEW_TYPE_ITEM_HEADER, systemHeaderHolder.getItemViewType());

        final RingtoneHolder silentHolder = (RingtoneHolder) itemsIter.next();
        assertEquals(Utils.RINGTONE_SILENT, silentHolder.getUri());
        assertEquals(RingtoneViewHolder.VIEW_TYPE_SYSTEM_SOUND, silentHolder.getItemViewType());

        final RingtoneHolder defaultHolder = (RingtoneHolder) itemsIter.next();
        assertEquals(RingtoneViewHolder.VIEW_TYPE_SYSTEM_SOUND, defaultHolder.getItemViewType());

        Runnable assertRunnable = () -> {
            assertEquals("Silent", silentHolder.getName());
            assertEquals("Default alarm sound", defaultHolder.getName());
            assertEquals(DataModel.getDataModel().getDefaultAlarmRingtoneUri(),
                    defaultHolder.getUri());
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(assertRunnable);

        Runnable removeCustomRingtoneRunnable = () -> {
            DataModel.getDataModel().removeCustomRingtone(CUSTOM_RINGTONE_1);
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(removeCustomRingtoneRunnable);
    }

    private void createTimerRingtonePickerActivity() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Intent newIntent = new Intent();

        Runnable createIntentRunnable = () -> {
            final Intent intent = RingtonePickerActivity.createTimerRingtonePickerIntent(context);
            newIntent.fillIn(intent, 0);
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(createIntentRunnable);

        createRingtonePickerActivity(newIntent);
    }

    private void createAlarmRingtonePickerActivity(Uri ringtone) {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Intent newIntent = new Intent();

        Runnable createIntentRunnable = () -> {
            // Use the custom ringtone in some alarms.
            final Alarm alarm = new Alarm(1, 1);
            alarm.enabled = true;
            alarm.vibrate = true;
            alarm.alert = ringtone;
            alarm.deleteAfterUse = true;

            final Intent intent =
                    RingtonePickerActivity.createAlarmRingtonePickerIntent(context, alarm);
            newIntent.fillIn(intent, 0);
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(createIntentRunnable);

        createRingtonePickerActivity(newIntent);
    }

    @SuppressWarnings("unchecked")
    private void createRingtonePickerActivity(Intent intent) {
        activity = rule.launchActivity(intent);
        ringtoneList = activity.findViewById(R.id.ringtone_content);
        ringtoneAdapter = (ItemAdapter<ItemHolder<Uri>>) ringtoneList.getAdapter();
    }
}
