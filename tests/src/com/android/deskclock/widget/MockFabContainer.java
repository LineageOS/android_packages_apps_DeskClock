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

package com.android.deskclock.widget;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.android.deskclock.DeskClockFragment;
import com.android.deskclock.FabContainer;

/**
 * DeskClock is the normal container for the fab and its left and right buttons. In order to test
 * each tab in isolation, tests avoid inflating all of DeskClock and instead set this mock fab
 * container into the DeskClockFragment under test. It mimics the behavior of a fab container,
 * albeit without animation, so fragment tests can verify the state of the fab any time they like.
 */
public final class MockFabContainer implements FabContainer {

    private final DeskClockFragment deskClockFragment;

    private ImageView fab;
    private Button leftButton;
    private Button rightButton;

    public MockFabContainer(DeskClockFragment fragment, Context context) {
        deskClockFragment = fragment;
        fab = new ImageView(context);
        leftButton = new ImageView(context);
        rightButton = new ImageView(context);

        updateFab(FabContainer.FAB_AND_BUTTONS_IMMEDIATE);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deskClockFragment.onFabClick(fab);
            }
        });
        leftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deskClockFragment.onLeftButtonClick(leftButton);
            }
        });
        rightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deskClockFragment.onRightButtonClick(rightButton);
            }
        });
    }

    @Override
    public void updateFab(@UpdateFabFlag int updateType) {
        if ((updateType & FabContainer.FAB_ANIMATION_MASK) != 0) {
            deskClockFragment.onUpdateFab(fab);
        }
        if ((updateType & FabContainer.FAB_REQUEST_FOCUS_MASK) != 0) {
            fab.requestFocus();
        }
        if ((updateType & FabContainer.BUTTONS_ANIMATION_MASK) != 0) {
            deskClockFragment.onUpdateFabButtons(leftButton, rightButton);
        }
        if ((updateType & FabContainer.BUTTONS_DISABLE_MASK) != 0) {
            leftButton.setClickable(false);
            rightButton.setClickable(false);
        }
    }

    public ImageView getFab() {
        return fab;
    }

    public Button getLeftButton() {
        return leftButton;
    }

    public Button getRightButton() {
        return rightButton;
    }
}
