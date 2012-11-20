/*
 * Copyright (C) 2012 The CyanogenMod Project
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

import android.app.AlertDialog.Builder;
/*import android.app.Profile;
import android.app.ProfileManager;*/
import android.content.Context;
import android.content.DialogInterface;
import android.preference.ListPreference;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A {@link Preference} class for associate a profile with
 * an alarm, allow change the profile when the alarm is
 * activate.
 */
public class ProfilePreference extends ListPreference {

    private final CharSequence[] mNames;
    private final CharSequence[] mUuids;
    private UUID mProfile;

    private final Context mContext;

    /**
     * Constructor of <code>ProfilePreference</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public ProfilePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        /*final ProfileManager profileManager =
                (ProfileManager)context.getSystemService(Context.PROFILE_SERVICE);*/

        // Load the list of defined profiles
        List<CharSequence> names = new ArrayList<CharSequence>();
        List<CharSequence> uuids = new ArrayList<CharSequence>();

        // No profile selection
        names.add(context.getString(R.string.alarm_profile_no_change_profile));
        uuids.add(Alarm.NO_PROFILE.toString()); // No UUID

        // Get names and uuids of current list of profiles
        /*if( profileManager != null ) {
            Profile[] profiles = profileManager.getProfiles();
            if (profiles != null) {
                for (int i = 0; i < profiles.length; i++) {
                    names.add(profiles[i].getName());
                    uuids.add(profiles[i].getUuid().toString());
                }
            }
        }*/

        mNames = names.toArray(new CharSequence[names.size()]);
        mUuids = uuids.toArray(new CharSequence[uuids.size()]);
        setEntries(mNames);
        setEntryValues(mUuids);
    }
    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        builder.setSingleChoiceItems(
                mNames, getPosition(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which < 0){
                    return;
                }
                mProfile = UUID.fromString(mUuids[which].toString());
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            if (mProfile != null) {
                fillSummary();
                callChangeListener(mProfile);
            }
        }
    }

    public UUID getProfile() {
        return this.mProfile;
    }

    public void setProfile(UUID profile) {
        this.mProfile = profile;
        fillSummary();
    }

    private void fillSummary() {
        if (getPosition() == 0) {
            setSummary(mContext.getString(R.string.alarm_profile_summary_no_change));
        } else {
            // Search the selected profile
            CharSequence name = mNames[getPosition()];
            setSummary(mContext.getString(R.string.alarm_profile_summary_change_to, name));
        }
    }

    private int getPosition() {
        if (mProfile != null ) {
            for (int i = 0; i < this.mUuids.length; i++) {
                if (this.mUuids[i].equals(mProfile.toString())) {
                    return i;
                }
            }
        }
        return 0;
    }

}
