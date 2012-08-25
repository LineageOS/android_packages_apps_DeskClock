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

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.widget.NumberPicker;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * A widget for display a digital timer clock (a clock that displays the difference in hours,
 * minutes, seconds and milliseconds from 2 millisecond references)
 */
public abstract class DigitalTimerClock extends RelativeLayout {

    /**
     * An interface to listen when the digital clock enters and exits
     * of edition mode
     */
    public interface OnEditListener {
        /**
         * Invoked when the digital enters in edition mode
         */
        public void OnEnterEdit();
        /**
         * Invoked when the digital exits in edition mode
         */
        public void OnExitEdit();
    }

    private static final String LOG_TAG = "DigitalTimerClock"; //$NON-NLS-1$

    // Blink effect animation duration
    private static final int BLINK_FADE_EFFECT_DURATION = 250;

    // When autoresize, the aspect ratio of the text respect the original mTextSize
    private static final float AUTORESIZE_ASPECT_RATIO = 12.0f;

    // The aspect ratio for the milliseconds text respect the mTextSize used for the rest of digits
    private static final float MILLISECONDS_TEXT_SIZE_ASPECT_RATIO = 50.0f;

    // The base number identifier to create new view identifiers
    private static final int VIEW_IDENTIFIER_NUMBER_BASE = 999999;

    // The main timer clock [hours, :, minutes, :, seconds; ., milliseconds]
    private TextView[] mTimerClock;
    private NumberPicker[] mTimerClockEditor;
    private Handler mHandler;

    private boolean mIsEditMode;
    private boolean mForceRedraw;
    private long mLockedTime;
    private long mLastLock;

    private long mElapsed;

    // Properties
    private int mTextSize;
    private boolean mAutoresize;
    private boolean mEditable;
    private int mPadding;
    private int mColor;
    private boolean mShowSign;
    private long mDefaultValue;
    private int mPositiveColor;
    private int mNegativeColor;

    private OnEditListener mOnEditListener;

    /**
     * Constructor of <code>DigitalTimerClock</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public DigitalTimerClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DigitalTimerClock);
        try {
            init(a);
        } finally {
            a.recycle();
        }
    }

    /**
     * Constructor of <code>DigitalTimerClock</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyle The default style to apply to this view. If 0, no style
     *        will be applied (beyond what is included in the theme). This may
     *        either be an attribute resource, whose value will be retrieved
     *        from the current theme, or an explicit style resource.
     */
    public DigitalTimerClock(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.DigitalTimerClock, defStyle, 0);
        try {
            init(a);
        } finally {
            a.recycle();
        }
    }

    /**
     * Method that initializes the widget. This method creates the 7 {@link TextView}
     * that displays the timer clock
     *
     * @param tarray The TypedArray
     */
    private void init(TypedArray tarray) {
        this.mHandler = new Handler();

        // Retrieve attributes
        this.mAutoresize = tarray.getBoolean(R.styleable.DigitalTimerClock_autoresize, false);
        this.mEditable = tarray.getBoolean(R.styleable.DigitalTimerClock_editable, false);
        this.mTextSize = (int) tarray.getDimension(R.styleable.DigitalTimerClock_size, 56.0f);
        this.mShowSign = tarray.getBoolean(R.styleable.DigitalTimerClock_show_sign, false);
        this.mDefaultValue = tarray.getInt(R.styleable.DigitalTimerClock_default_value, 0);
        this.mPadding = (int) tarray.getDimension(
                R.styleable.DigitalTimerClock_padding, 8.0f);
        this.mColor =
                tarray.getColor(
                        R.styleable.DigitalTimerClock_color, android.R.color.holo_blue_dark);
        this.mPositiveColor =
                tarray.getColor(
                        R.styleable.DigitalTimerClock_positive_color,
                        android.R.color.holo_green_dark);
        this.mNegativeColor =
                tarray.getColor(
                        R.styleable.DigitalTimerClock_negative_color,
                        android.R.color.holo_red_dark);

        // Initialize internal properties
        this.mIsEditMode = false;
        this.mForceRedraw = false;
        this.mLockedTime = 0;
        this.mLastLock = 0;
        this.mElapsed = this.mDefaultValue;

        // Create the timer clock (milliseconds has a 50% of text size)
        TextView lastView = null;
        this.mTimerClock = new TextView[7];
        for (int i=0; i<6; i++) {
            lastView = createTimerDigit(VIEW_IDENTIFIER_NUMBER_BASE + i, this.mTextSize, lastView);
            this.mTimerClock[i] = lastView;
        }
        int millisTextSize = (int)(this.mTextSize * MILLISECONDS_TEXT_SIZE_ASPECT_RATIO) / 100;
        lastView = createTimerDigit(VIEW_IDENTIFIER_NUMBER_BASE + 6, millisTextSize, lastView);
        this.mTimerClock[6] = lastView;

        // Set default text
        this.mTimerClock[1].setText(":");  //$NON-NLS-1$
        this.mTimerClock[3].setText(":");  //$NON-NLS-1$
        this.mTimerClock[5].setText(".");  //$NON-NLS-1$
        updateTime(this.mDefaultValue);

        // Create the editor
        if (this.mEditable) {
            this.mTimerClockEditor = new NumberPicker[7];
            this.mTimerClockEditor[0] =
                    createNumberPicker(
                            999,
                            this.mTimerClock[0],
                            this.mTimerClock[0],
                            null);
            this.mTimerClockEditor[2] =
                    createNumberPicker(
                            59,
                            this.mTimerClock[2],
                            this.mTimerClock[0],
                            "%02d");  //$NON-NLS-1$
            this.mTimerClockEditor[4] =
                    createNumberPicker(
                            59,
                            this.mTimerClock[4],
                            this.mTimerClock[0],
                            "%02d");  //$NON-NLS-1$
            this.mTimerClockEditor[6] =
                    createNumberPicker(
                            999,
                            this.mTimerClock[6],
                            this.mTimerClock[0],
                            "%03d"); //$NON-NLS-1$
            setOnLongClickListener( new View.OnLongClickListener() {
                @SuppressWarnings("synthetic-access")
                public boolean onLongClick(View v) {
                    //Show the editor
                    try {
                        if (DigitalTimerClock.this.isEditable()) {
                            //Return haptic feedback
                            Vibrator vibrator =
                                    (Vibrator)getContext().getSystemService(Context.VIBRATOR_SERVICE);
                            vibrator.vibrate(250);

                            showEditor();
                            return true;
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Fails open editor.", e); //$NON-NLS-1$
                    }
                    return false;
                }
            });
        }
    }

    /**
     * Method that creates a timer digit
     *
     * @param id The identifier to assign to the view
     * @param textSize The text size of the digit
     * @param toLeftOf Align the new textview to the right of the argument
     * @return TextView The that represents the digit
     */
    private TextView createTimerDigit(int id, int textSize, TextView toRigthOf) {
        // Create the TextView
        TextView digit = new TextView(getContext());
        digit.setId(id);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        if (toRigthOf != null) {
            params.addRule(RelativeLayout.RIGHT_OF, toRigthOf.getId());
            params.addRule(RelativeLayout.ALIGN_BASELINE, toRigthOf.getId());
        }
        digit.setLayoutParams(params);
        digit.setPadding(0, this.mPadding, 0, this.mPadding);
        digit.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        digit.setTextColor(this.mColor);
        digit.setFocusable(false);
        digit.setFocusableInTouchMode(false);
        addView(digit);
        return digit;
    }

    /**
     * Method that creates a timer digit
     *
     * @param maxValue The max value of the number picker
     * @param toAlignWith The textview to align with
     * @param toHeightOf The textview of which use his height
     * @return NumberPicker The that represents the number picker
     */
    private NumberPicker createNumberPicker(
            int maxValue, TextView toAlignWith, TextView toHeightOf, final String format) {
        // Create the NumberPicker
        NumberPicker digit = new NumberPicker(getContext());
        LayoutParams params = new RelativeLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_LEFT, toAlignWith.getId());
        params.addRule(RelativeLayout.ALIGN_RIGHT, toAlignWith.getId());
        params.addRule(RelativeLayout.ALIGN_TOP, toHeightOf.getId());
        params.addRule(RelativeLayout.ALIGN_BOTTOM, toHeightOf.getId());
        digit.setLayoutParams(params);
        digit.setMinValue(0);
        digit.setMaxValue(maxValue);
        digit.setValue(0);
        digit.setWrapSelectorWheel(false);
        digit.setFocusable(false);
        digit.setFocusableInTouchMode(false);
        digit.setVisibility(View.INVISIBLE);
        if (format != null) {
            digit.setFormatter(new NumberPicker.Formatter() {
                public String format(int value) {
                    return String.format(format, Integer.valueOf(value));
                }
            });
        }
        addView(digit);
        return digit;
    }

    /**
     * Method that show the numeric editor
     *
     * @param show Indicates if the editor must be shown or hidden
     */
    private void showHideEditor(boolean show) {
        for (int i = 0; i < this.mTimerClockEditor.length; i++) {
            if (this.mTimerClockEditor[i] != null) {
                this.mTimerClock[i].setVisibility( show ? View.INVISIBLE : View.VISIBLE);
                this.mTimerClockEditor[i].setVisibility( show ? View.VISIBLE : View.INVISIBLE);
                this.mIsEditMode = show;
            }
        }

        // Notify
        if (this.mOnEditListener != null) {
            if (show) {
                this.mOnEditListener.OnEnterEdit();
            } else {
                this.mOnEditListener.OnExitEdit();
            }
        }
    }

    /**
     * Method that sets the listener where return edition events
     *
     * @param onEditListener The listener where return edition events
     */
    public void setOnEditListener(OnEditListener onEditListener) {
        this.mOnEditListener = onEditListener;
    }

    /**
     * Method that returns if the clock is editable
     *
     * @return boolean If the clock is editable
     */
    public boolean isEditable() {
        return this.mEditable;
    }

    /**
     * Method that sets if the clock is editable
     *
     * @param editable If the clock is editable
     */
    public void setEditable(boolean editable) {
        this.mEditable = editable;
    }

    /**
     * Method that returns if the main clock is in edition mode
     *
     * @return boolean If the clock is in edition mode
     */
    public boolean isEditMode() {
        return this.mIsEditMode;
    }

    /**
     * Method that open the editor
     *
     * @throws IllegalAccessException If the clock not support edition
     */
    public void showEditor() throws IllegalAccessException {
        if (!this.mEditable) {
            throw new IllegalAccessException("Clock is not editable"); //$NON-NLS-1$
        }
        if (!this.mIsEditMode) {
            // Convert to the appropriate amount of time
            long[] time = TimerHelper.obtainTime(DigitalTimerClock.this.mElapsed);
            DigitalTimerClock.this.mTimerClockEditor[0].setValue((int)time[0]);
            DigitalTimerClock.this.mTimerClockEditor[2].setValue((int)time[1]);
            DigitalTimerClock.this.mTimerClockEditor[4].setValue((int)time[2]);
            DigitalTimerClock.this.mTimerClockEditor[6].setValue((int)time[3]);

            showHideEditor(true);
        }
    }

    /**
     * Method that cancels the edition
     *
     * @throws IllegalAccessException If the clock not support edition
     */
    public void cancelEditor() throws IllegalAccessException {
        if (!this.mEditable) {
            throw new IllegalAccessException("Clock is not editable"); //$NON-NLS-1$
        }
        if (this.mIsEditMode) {
            showHideEditor(false);
        }
    }

    /**
     * Method that sets the clock with the current value of edition
     *
     * @throws IllegalAccessException If the clock not support edition
     */
    public void setEditor() throws IllegalAccessException {
        if (!this.mEditable) {
            throw new IllegalAccessException("Clock is not editable"); //$NON-NLS-1$
        }
        // Translate the current editor values into milliseconds
        long millis = TimerHelper.toTime(
                                    this.mTimerClockEditor[0].getValue(),
                                    this.mTimerClockEditor[2].getValue(),
                                    this.mTimerClockEditor[4].getValue(),
                                    this.mTimerClockEditor[6].getValue()
                                         );
        this.mForceRedraw = true;
        this.mElapsed = millis;
        this.mDefaultValue = millis;
        updateTimeOnUIThread(millis);

        showHideEditor(false);
    }

    /**
     * Method that returns if the clock is in the default value
     *
     * @return boolean If the clock has the default value
     */
    public boolean isInDefaultValue() {
        return getTime() == this.mDefaultValue;
    }

    /**
     * Method that returns the default value of the clock
     *
     * @return long The default value
     */
    public long getDefaultValue() {
        return this.mDefaultValue;
    }

    /**
     * Method that sets the default value of the clock
     *
     * @param defaultValue The default value
     * @param reset If the clock must be reset to default value
     */
    public void setDefaultValue(long defaultValue, boolean reset) {
        this.mDefaultValue = defaultValue;
        if (reset) {
            reset();
        }
    }

    /**
     * Method that returns the color of the clock
     *
     * @return int The color of the clock
     */
    public int getColor() {
        return this.mColor;
    }

    /**
     * Method that sets the color of the clock
     *
     * @param color The new color
     */
    public void setColor(int color) {
        for (int i = 0; i < this.mTimerClockEditor.length; i++) {
            if (this.mTimerClock[i] != null) {
                this.mTimerClock[i].setTextColor(color);
            }
        }
        this.mColor = color;
    }

    /**
     * Method that resets the clock to the default value
     */
    public void reset() {
        this.mForceRedraw = true;
        this.mLastLock = this.mDefaultValue;
        updateTimeOnUIThread(this.mDefaultValue);
    }

    /**
     * Method that update the time of the clock and then pauses the clock
     * refresh by the specific time
     *
     * @param elapsed The elapsed time to display
     * @param millis The milliseconds that the clock is not refreshing
     */
    public void updateTimeAndLock(long elapsed, long millis) {
        this.mForceRedraw = true;
        updateTimeOnUIThread(elapsed);
        this.mLockedTime = millis;
        this.mLastLock = System.currentTimeMillis();
    }

    /**
     * Method that blinks the digital clock. This method can be used to notify
     * visually the end of a countdown timer.
     *
     * @param hide
     * @param count The number of flashes or {@link Animation.INFINITE} to
     * infinite loop
     */
    protected void blink(final boolean hide, final int count) {
        AnimationSet blinkEffect = new AnimationSet(true);
        float start = getVisibility() == View.VISIBLE ? 1.0f : 0.0f;
        float end = getVisibility() == View.VISIBLE ? 0.0f : 1.0f;
        AlphaAnimation alpha = new AlphaAnimation(start, end);
        alpha.setDuration(BLINK_FADE_EFFECT_DURATION);
        alpha.setRepeatMode(Animation.REVERSE);
        alpha.setRepeatCount(count);
        alpha.setAnimationListener(new Animation.AnimationListener() {
            public void onAnimationStart(Animation animation) {/**NON BLOCK**/}

            public void onAnimationRepeat(Animation animation) {/**NON BLOCK**/}

            public void onAnimationEnd(Animation animation) {
                // Hide clock
                if (hide) {
                    setVisibility(View.INVISIBLE);
                }
            }
        });
        blinkEffect.addAnimation(alpha);
        startAnimation(blinkEffect);
    }

    /**
     * Method that removes the blink effect if blink effect is present
     */
    protected void unblink() {
        //Remove the blink animation if present
        clearAnimation();
    }

    /**
     * Method that update the time of the digital timer with the elapsed time on a
     * UI thread.
     *
     * @param elapsed The time in milliseconds that elapsed since the timer was started
     */
    protected void updateTimeOnUIThread(final long elapsed) {
        this.mHandler.postAtFrontOfQueue(new Runnable() {
            public void run() {
                updateTime(elapsed);
            }
        });
    }

    /**
     * Method that update the time of the digital timer with the elapsed time.
     *
     * @param elapsed The time in milliseconds that elapsed since the timer was started
     */
    @SuppressWarnings("boxing")
    public synchronized void updateTime(long elapsed) {
        // Not allow update UI if widget is in editing mode
        if (this.mIsEditMode) {
            return;
        }

        // Locked ?
        long now = System.currentTimeMillis();
        if ( !this.mForceRedraw &&
             this.mLockedTime != 0 && (now - this.mLastLock) < this.mLockedTime ) {
            return;
        }
        if (!this.mForceRedraw) {
            this.mLockedTime = 0;
        }
        this.mForceRedraw = false;

        // Convert to the appropriate amount of time
        long[] time = TimerHelper.obtainTime(elapsed);
        String hours = String.valueOf(time[0]);
        if (this.mShowSign) {
            hours = (elapsed >= 0 ? "+" : "-") + hours;  //$NON-NLS-1$//$NON-NLS-2$
        }
        adjustTextSize(hours);
        this.mTimerClock[0].setText(hours);                           // Hours
        this.mTimerClock[2].setText(String.format("%02d", time[1]));  // Minutes  //$NON-NLS-1$
        this.mTimerClock[4].setText(String.format("%02d", time[2]));  // Seconds  //$NON-NLS-1$
        this.mTimerClock[6].setText(String.format("%03d", time[3]));  // Milliseconds //$NON-NLS-1$
        if (this.mShowSign) {
            int color = elapsed >= 0 ? this.mPositiveColor : this.mNegativeColor;
            for (int i = 0; i < this.mTimerClock.length; i++) {
                this.mTimerClock[i].setTextColor(color);
            }
        }

        // Save the time
        this.mElapsed = elapsed;
    }

    /**
     * Method that returns the time of the clock
     *
     * @return long The time of the clock
     */
    public long getTime() {
        return this.mElapsed;
    }

    /**
     * Method that adjust the size of the text
     * @param hour
     */
    private void adjustTextSize(String hour) {
        if (this.mAutoresize) {
            // Reduce the current text size proportionally to the number digits shown
            // by the hours. Other digits remains unchanged (fixed chars)
            int oldLength = this.mTimerClock[0].getText().length();
            int newLength = hour.length();
            if (oldLength != newLength && oldLength > 0) {
                int newTextSize = this.mTextSize
                        - (int)(((this.mTextSize * AUTORESIZE_ASPECT_RATIO) / 100) * (newLength-1));
                int newMillisTextSize =
                        (int)(newTextSize * MILLISECONDS_TEXT_SIZE_ASPECT_RATIO) / 100;
                this.mTimerClock[0].setTextSize(TypedValue.COMPLEX_UNIT_SP, newTextSize);
                this.mTimerClock[2].setTextSize(TypedValue.COMPLEX_UNIT_SP, newTextSize);
                this.mTimerClock[4].setTextSize(TypedValue.COMPLEX_UNIT_SP, newTextSize);
                this.mTimerClock[6].setTextSize(TypedValue.COMPLEX_UNIT_SP, newMillisTextSize);
            }
        }
    }

}
