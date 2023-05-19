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

package com.android.alarmclock;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;

import com.android.deskclock.R;
import com.android.deskclock.Utils;

public final class WidgetUtils {

    private static final String PREFS_NAME = "com.android.alarmclock.widgets";
    private static final String PREF_PREFIX_KEY = "appwidget_";
    private static final String PREF_MODE_PREFIX = PREF_PREFIX_KEY + "solid_";


    private WidgetUtils() {}

    // Calculate the scale factor of the fonts in the widget
    public static float getScaleRatio(Context context, Bundle options, int id, int cityCount) {
        if (options == null) {
            AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
            if (widgetManager == null) {
                // no manager , do no scaling
                return 1f;
            }
            options = widgetManager.getAppWidgetOptions(id);
        }
        if (options != null) {
            int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            if (minWidth == 0) {
                // No data , do no scaling
                return 1f;
            }
            final Resources res = context.getResources();
            float density = res.getDisplayMetrics().density;
            float ratio = (density * minWidth) / res.getDimension(R.dimen.min_digital_widget_width);
            ratio = Math.min(ratio, getHeightScaleRatio(context, options, id));
            ratio *= .83f;

            if (cityCount > 0) {
                return Math.min(ratio, 1f);
            }

            ratio = Math.min(ratio, 1.6f);
            if (Utils.isPortrait(context)) {
                ratio = Math.max(ratio, .71f);
            }
            else {
                ratio = Math.max(ratio, .45f);
            }
            return ratio;
        }
        return 1f;
    }

    // Calculate the scale factor of the fonts in the list of  the widget using the widget height
    private static float getHeightScaleRatio(Context context, Bundle options, int id) {
        if (options == null) {
            AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
            if (widgetManager == null) {
                // no manager , do no scaling
                return 1f;
            }
            options = widgetManager.getAppWidgetOptions(id);
        }
        if (options != null) {
            int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
            if (minHeight == 0) {
                // No data , do no scaling
                return 1f;
            }
            final Resources res = context.getResources();
            float density = res.getDisplayMetrics().density;
            float ratio = density * minHeight / res.getDimension(R.dimen.min_digital_widget_height);
            if (Utils.isPortrait(context)) {
                return ratio * 1.75f;
            }
            return ratio;
        }
        return 1;
    }

    public static void saveWidgetMode(Context context, int appWidgetId, boolean isSolid) {
        context.getSharedPreferences(PREFS_NAME, 0).edit()
                .putBoolean(PREF_MODE_PREFIX + appWidgetId, isSolid)
                .commit();
    }

    public static boolean getWidgetMode(Context context, int appWidgetId) {
        return context.getSharedPreferences(PREFS_NAME, 0)
                .getBoolean(PREF_MODE_PREFIX + appWidgetId, false);
    }

    public static int[] getWidgetLayouts(Context context, int appWidgetId) {
        int[] layoutIds = new int[2];
        if (getWidgetMode(context, appWidgetId)) {
            layoutIds[0] = R.layout.digital_widget_darkbg_solid_theme_root;
            layoutIds[1] = R.layout.digital_widget_lightbg_solid_theme_root;
        } else {
            layoutIds[0] = R.layout.digital_widget_darkbg_theme_root;
            layoutIds[1] = R.layout.digital_widget_lightbg_theme_root;
        }
        return layoutIds;
    }
}
