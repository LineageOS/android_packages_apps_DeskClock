/*
 * Copyright (C) 2013 Andrew Neal
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

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;

/**
 * A {@link DialogFragment} used to explain to the user what the
 * "Bedtime calculator" does.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class BedtimeCling extends DialogFragment {

    /**
     * Empty constructor as per the {@link Fragment} docs
     */
    public BedtimeCling() {
    }

    /**
     * @return A new instance of {@link BedtimeCling}
     */
    public static BedtimeCling newInstance() {
        final BedtimeCling bedtimeCling = new BedtimeCling();
        return bedtimeCling;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.bedtime_cling, null);
        return rootView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final Dialog dialog = getDialog();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Button ok = (Button) view.findViewById(R.id.bedtime_ok);
        ok.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }
}
