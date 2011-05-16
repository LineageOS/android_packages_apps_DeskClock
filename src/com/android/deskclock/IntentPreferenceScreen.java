/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.deskclock;

import java.net.URISyntaxException;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

/**
 * Preference used to conveniently store a reference to an Intent. Will
 * automatically update the UI with the application's title and icon as
 * appropriate.
 */
public class IntentPreferenceScreen extends Preference {

    private Drawable mIcon;

    private ImageView mIconView;

    private Intent mIntent;

    public IntentPreferenceScreen(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IntentPreferenceScreen(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setLayoutResource(R.layout.preference_intent);
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.IntentPreferenceScreen, defStyle, 0);
        mIcon = a.getDrawable(R.styleable.IntentPreferenceScreen_icon);
    }

    /**
     * Update the stored intent
     * 
     * @param intent new Intent
     */
    public void updateIntent(Intent intent) {
        mIntent = intent;

        if (mIntent != null) {
            setSummary(intent.toUri(Intent.URI_INTENT_SCHEME));
            try {
                String packageName = mIntent.getComponent().getPackageName();
                PackageManager packageManager = getContext().getPackageManager();
                ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
                setSummary(packageManager.getApplicationLabel(info));
                mIcon = packageManager.getApplicationIcon(packageName);
            } catch (NameNotFoundException e) {
                Log.e("Could not find package", e);
            }
        } else {
            setSummary("");
            mIcon = null;
        }

        updateIcon();
    }

    /**
     * Update the stored intent based on the string representation of the
     * intent. See {@link #getIntentString()}.
     * 
     * @param intentUri string representation of the new Intent to use
     */
    public void updateIntent(String intentUri) {
        if (intentUri == null) {
            clearIntent();
        }

        try {
            updateIntent(Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME));
        } catch (URISyntaxException e) {
            Log.i("Unable to parse intent: " + intentUri);
        }
    }

    /**
     * Erases the stored intent and resets the UI
     */
    public void clearIntent() {
        updateIntent((Intent) null);
    }

    /**
     * @return a string representation of the saved intent or null if no intent
     *         set
     */
    public String getIntentString() {
        if (mIntent == null)
            return null;

        return mIntent.toUri(Intent.URI_INTENT_SCHEME);
    }

    private void updateIcon() {
        if (mIconView != null) {
            mIconView.setImageDrawable(mIcon);
        }
    }

    @Override
    public void onBindView(View view) {
        super.onBindView(view);
        mIconView = (ImageView) view.findViewById(android.R.id.icon);
        updateIcon();
    }
}
