/*
 * Copyright (c) 2016 The CyanogenMod Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.android.deskclock.R;

/**
 * {@link MenuItemController} for setting menu.
 */
public final class WidgetMenuItemController extends AbstractMenuItemController {

    private static final String LOG_TAG = "DeskClock";

    private static final String LC_PACKAGE = "com.cyanogenmod.lockclock";
    private static final String LC_ACTIVITY = LC_PACKAGE + ".preference.Preferences";
    private static final ComponentName sWidgetSettingComponentName = new ComponentName
            (LC_PACKAGE, LC_ACTIVITY);

    private static final int WIDGET_MENU_RES_ID = R.id.menu_item_widget_settings;
    private final Context mContext;

    public WidgetMenuItemController(Context context) {
        mContext = context;
    }

    @Override
    public int getId() {
        return WIDGET_MENU_RES_ID;
    }

    @Override
    public void showMenuItem(Menu menu) {
        menu.findItem(WIDGET_MENU_RES_ID).setVisible(isPackageInstalled(LC_PACKAGE));
    }

    @Override
    public boolean handleMenuItemClick(MenuItem item) {
        Intent wsi = new Intent();
        wsi.setComponent(sWidgetSettingComponentName);
        mContext.startActivity(wsi);
        return true;
    }

    private boolean isPackageInstalled(String pkg) {
        if (pkg == null) {
            return false;
        }
        try {
            PackageInfo pi = mContext.getPackageManager().getPackageInfo(pkg, 0);
            if (!pi.applicationInfo.enabled) {
                return false;
            } else {
                return true;
            }
        } catch (NameNotFoundException e) {
            return false;
        }
    }
}
