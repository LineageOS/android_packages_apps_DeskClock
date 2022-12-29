/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.content.res.Resources;

import androidx.annotation.IdRes;

import android.text.BidiFormatter;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.deskclock.FabContainer;
import com.android.deskclock.FormattedTextUtils;
import com.android.deskclock.R;
import com.android.deskclock.uidata.UiDataModel;

import java.io.Serializable;
import java.util.Arrays;

import static com.android.deskclock.FabContainer.FAB_REQUEST_FOCUS;
import static com.android.deskclock.FabContainer.FAB_SHRINK_AND_EXPAND;

public class TimerSetupView extends LinearLayout implements View.OnClickListener,
        View.OnLongClickListener {

    private final int[] mInput = { 0, 0, 0, 0, 0, 0 };

    private int mInputPointer = -1;
    private final CharSequence mTimeTemplate;

    private TextView mTimeView;
    private View mDeleteView;
    private TextView[] mDigitViews;

    /** Updates to the fab are requested via this container. */
    private FabContainer mFabContainer;

    public TimerSetupView(Context context) {
        this(context, null /* attrs */);
    }

    public TimerSetupView(Context context, AttributeSet attrs) {
        super(context, attrs);

        final BidiFormatter bf = BidiFormatter.getInstance(false /* rtlContext */);
        final String hoursLabel = bf.unicodeWrap(context.getString(R.string.hours_label));
        final String minutesLabel = bf.unicodeWrap(context.getString(R.string.minutes_label));
        final String secondsLabel = bf.unicodeWrap(context.getString(R.string.seconds_label));

        // Create a formatted template for "00h 00m 00s".
        mTimeTemplate = TextUtils.expandTemplate("^1^4 ^2^5 ^3^6",
                bf.unicodeWrap("^1"),
                bf.unicodeWrap("^2"),
                bf.unicodeWrap("^3"),
                FormattedTextUtils.formatText(hoursLabel, new RelativeSizeSpan(0.5f)),
                FormattedTextUtils.formatText(minutesLabel, new RelativeSizeSpan(0.5f)),
                FormattedTextUtils.formatText(secondsLabel, new RelativeSizeSpan(0.5f)));

        LayoutInflater.from(context).inflate(R.layout.timer_setup_view, this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTimeView = findViewById(R.id.timer_setup_time);
        mDeleteView = findViewById(R.id.timer_setup_delete);
        mDigitViews = new TextView[] {
                findViewById(R.id.timer_setup_digit_0),
                findViewById(R.id.timer_setup_digit_1),
                findViewById(R.id.timer_setup_digit_2),
                findViewById(R.id.timer_setup_digit_3),
                findViewById(R.id.timer_setup_digit_4),
                findViewById(R.id.timer_setup_digit_5),
                findViewById(R.id.timer_setup_digit_6),
                findViewById(R.id.timer_setup_digit_7),
                findViewById(R.id.timer_setup_digit_8),
                findViewById(R.id.timer_setup_digit_9),
        };

        // Initialize the digit buttons.
        final UiDataModel uidm = UiDataModel.getUiDataModel();
        for (final TextView digitView : mDigitViews) {
            final int digit = getDigitForId(digitView.getId());
            digitView.setText(uidm.getFormattedNumber(digit, 1));
            digitView.setOnClickListener(this);
        }
        TextView doubleZero = findViewById(R.id.timer_setup_digit_00);
        doubleZero.setText(uidm.getFormattedNumber(0, 2));
        doubleZero.setOnClickListener(this);

        mDeleteView.setOnClickListener(this);
        mDeleteView.setOnLongClickListener(this);

        updateTime();
    }

    public void setFabContainer(FabContainer fabContainer) {
        mFabContainer = fabContainer;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        View view = null;
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            view = mDeleteView;
        } else if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            view = mDigitViews[keyCode - KeyEvent.KEYCODE_0];
        }

        if (view != null) {
            final boolean result = view.performClick();
            if (result && hasValidInput()) {
                mFabContainer.updateFab(FAB_REQUEST_FOCUS);
            }
            return result;
        }

        return false;
    }

    @Override
    public void onClick(View view) {
        if (view == mDeleteView) {
            delete();
        } else if (view.getId() == R.id.timer_setup_digit_00) {
            append(0);
            append(0);
        } else {
            append(getDigitForId(view.getId()));
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (view == mDeleteView) {
            reset();
            updateFab();
            return true;
        }
        return false;
    }

    private int getDigitForId(@IdRes int id) {
        if (id == R.id.timer_setup_digit_0) {
            return 0;
        } else if (id == R.id.timer_setup_digit_1) {
            return 1;
        } else if (id == R.id.timer_setup_digit_2) {
            return 2;
        } else if (id == R.id.timer_setup_digit_3) {
            return 3;
        } else if (id == R.id.timer_setup_digit_4) {
            return 4;
        } else if (id == R.id.timer_setup_digit_5) {
            return 5;
        } else if (id == R.id.timer_setup_digit_6) {
            return 6;
        } else if (id == R.id.timer_setup_digit_7) {
            return 7;
        } else if (id == R.id.timer_setup_digit_8) {
            return 8;
        } else if (id == R.id.timer_setup_digit_9) {
            return 9;
        }
        throw new IllegalArgumentException("Invalid id: " + id);
    }

    private void updateTime() {
        final int seconds = mInput[1] * 10 + mInput[0];
        final int minutes = mInput[3] * 10 + mInput[2];
        final int hours = mInput[5] * 10 + mInput[4];

        final UiDataModel uidm = UiDataModel.getUiDataModel();
        SpannableString text = new SpannableString(TextUtils.expandTemplate(mTimeTemplate,
                uidm.getFormattedNumber(hours, 2),
                uidm.getFormattedNumber(minutes, 2),
                uidm.getFormattedNumber(seconds, 2)));

        final Resources r = getResources();
        int endIdx = text.length();
        int startIdx = seconds > 0 ? 8 : endIdx;
        startIdx = minutes > 0 ? 4 : startIdx;
        startIdx = hours > 0 ? 0 : startIdx;
        if (startIdx != endIdx) {
            final int highlightColor = r.getColor(R.color.accent_color, getContext().getTheme());
            text.setSpan(new ForegroundColorSpan(highlightColor), startIdx, endIdx, 0);
        }
        mTimeView.setText(text);
        mTimeView.setContentDescription(r.getString(R.string.timer_setup_description,
                r.getQuantityString(R.plurals.hours, hours, hours),
                r.getQuantityString(R.plurals.minutes, minutes, minutes),
                r.getQuantityString(R.plurals.seconds, seconds, seconds)));
    }

    private void updateFab() {
        mFabContainer.updateFab(FAB_SHRINK_AND_EXPAND);
    }

    private void append(int digit) {
        if (digit < 0 || digit > 9) {
            throw new IllegalArgumentException("Invalid digit: " + digit);
        }

        // Pressing "0" as the first digit does nothing.
        if (mInputPointer == -1 && digit == 0) {
            return;
        }

        // No space for more digits, so ignore input.
        if (mInputPointer == mInput.length - 1) {
            return;
        }

        // Append the new digit.
        System.arraycopy(mInput, 0, mInput, 1, mInputPointer + 1);
        mInput[0] = digit;
        mInputPointer++;
        updateTime();

        // Update TalkBack to read the number being deleted.
        mDeleteView.setContentDescription(getContext().getString(
                R.string.timer_descriptive_delete,
                UiDataModel.getUiDataModel().getFormattedNumber(digit)));

        // Update the fab, delete, and divider when we have valid input.
        if (mInputPointer == 0) {
            updateFab();
        }
    }

    private void delete() {
        // Nothing exists to delete so return.
        if (mInputPointer < 0) {
            return;
        }

        System.arraycopy(mInput, 1, mInput, 0, mInputPointer);
        mInput[mInputPointer] = 0;
        mInputPointer--;
        updateTime();

        // Update TalkBack to read the number being deleted or its original description.
        if (mInputPointer >= 0) {
            mDeleteView.setContentDescription(getContext().getString(
                    R.string.timer_descriptive_delete,
                    UiDataModel.getUiDataModel().getFormattedNumber(mInput[0])));
        } else {
            mDeleteView.setContentDescription(getContext().getString(R.string.timer_delete));
        }

        // Update the fab, delete, and divider when we no longer have valid input.
        if (mInputPointer == -1) {
            updateFab();
        }
    }

    public void reset() {
        if (mInputPointer != -1) {
            Arrays.fill(mInput, 0);
            mInputPointer = -1;
            updateTime();
        }
    }

    public boolean hasValidInput() {
        return mInputPointer != -1;
    }

    public long getTimeInMillis() {
        final int seconds = mInput[1] * 10 + mInput[0];
        final int minutes = mInput[3] * 10 + mInput[2];
        final int hours = mInput[5] * 10 + mInput[4];
        return seconds * DateUtils.SECOND_IN_MILLIS
                + minutes * DateUtils.MINUTE_IN_MILLIS
                + hours * DateUtils.HOUR_IN_MILLIS;
    }

    /**
     * @return an opaque representation of the state of timer setup
     */
    public Serializable getState() {
        return Arrays.copyOf(mInput, mInput.length);
    }

    /**
     * @param state an opaque state of this view previously produced by {@link #getState()}
     */
    public void setState(Serializable state) {
        final int[] input = (int[]) state;
        if (input != null && mInput.length == input.length) {
            for (int i = 0; i < mInput.length; i++) {
                mInput[i] = input[i];
                if (mInput[i] != 0) {
                    mInputPointer = i;
                }
            }
            updateTime();
        }
    }
}
