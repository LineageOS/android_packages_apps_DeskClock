/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Base activity class that changes the app window's color based on the current hour.
 */
public abstract class BaseActivity extends AppCompatActivity {

    /** Draws the app window's color. */
    private ColorDrawable mBackground;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow the content to layout behind the status and navigation bars.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        final @ColorInt int color = ThemeUtils.resolveColor(this, android.R.attr.windowBackground);
        adjustAppColor(color);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Ensure the app window color is up-to-date.
        final @ColorInt int color = ThemeUtils.resolveColor(this, android.R.attr.windowBackground);
        adjustAppColor(color);
    }

    /**
     * Adjusts the current app window color of this activity; animates the change if desired.
     *
     * @param color   the ARGB value to set as the current app window color
     */
    protected void adjustAppColor(@ColorInt int color) {
        // Create and install the drawable that defines the window color.
        if (mBackground == null) {
            mBackground = new ColorDrawable(color);
            getWindow().setBackgroundDrawable(mBackground);
        }

        final @ColorInt int currentColor = mBackground.getColor();
        if (currentColor != color) {
            setAppColor(color);
        }
    }

    private void setAppColor(@ColorInt int color) {
        mBackground.setColor(color);
    }
}
