/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.deskclock.actionbarmenu;

import static android.view.Menu.NONE;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

import com.android.deskclock.R;
import com.android.deskclock.settings.ScreensaverSettingsActivity;
import com.android.deskclock.settings.SettingsActivity;

/**
 * {@link MenuItemController} for settings menu.
 */
public final class ScreensaverSettingsMenuItemController implements MenuItemController {

    public static final int REQUEST_CHANGE_SETTINGS = 1;

    private static final int SS_SETTING_MENU_RES_ID = R.id.menu_item_ss_settings;

    private final Activity mActivity;

    public ScreensaverSettingsMenuItemController(Activity activity) {
        mActivity = activity;
    }

    @Override
    public int getId() {
        return SS_SETTING_MENU_RES_ID;
    }

    @Override
    public void onCreateOptionsItem(Menu menu) {
        menu.add(NONE, SS_SETTING_MENU_RES_ID, NONE, R.string.menu_item_ss_settings)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    }

    @Override
    public void onPrepareOptionsItem(MenuItem item) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //com.android.deskclock.settings.ScreensaverSettingsActivity
        final Intent ScreensaverSettingIntent = new Intent(mActivity, ScreensaverSettingsActivity.class);
        mActivity.startActivityForResult(ScreensaverSettingIntent, REQUEST_CHANGE_SETTINGS);
        return true;
    }
}
