/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.deskclock.alarms.dataadapter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.deskclock.AnimatorUtils;
import com.android.deskclock.ItemAdapter;
import com.android.deskclock.R;
import com.android.deskclock.ThemeUtils;
import com.android.deskclock.Utils;
import com.android.deskclock.alarms.AlarmTimeClickHandler;
import com.android.deskclock.data.DataModel;
import com.android.deskclock.events.Events;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.provider.AlarmInstance;
import com.android.deskclock.uidata.UiDataModel;

import java.util.List;

/**
 * A ViewHolder containing views for an alarm item in expanded state.
 */
public final class ExpandedAlarmViewHolder extends AlarmItemViewHolder {
    public static final int VIEW_TYPE = R.layout.alarm_time_expanded;

    private final TextView editLabel;
    private final ConstraintLayout repeatDays;
    private final CompoundButton[] dayButtons;
    private final CheckBox vibrate;
    private final TextView ringtone;
    private final TextView delete;

    private final boolean mHasVibrator;

    private ExpandedAlarmViewHolder(View itemView, boolean hasVibrator) {
        super(itemView);

        mHasVibrator = hasVibrator;

        delete = itemView.findViewById(R.id.delete);
        vibrate = itemView.findViewById(R.id.vibrate_onoff);
        ringtone = itemView.findViewById(R.id.choose_ringtone);
        editLabel = itemView.findViewById(R.id.edit_label);
        repeatDays = itemView.findViewById(R.id.repeat_days);

        final Context context = itemView.getContext();
        itemView.setBackground(new LayerDrawable(new Drawable[] {
                ContextCompat.getDrawable(context, R.drawable.alarm_background),
                ThemeUtils.resolveDrawable(context, R.attr.selectableItemBackground)
        }));

        // Build button for each day.
        final LayoutInflater inflater = LayoutInflater.from(context);
        final List<Integer> weekdays = DataModel.getDataModel().getWeekdayOrder().getCalendarDays();
        dayButtons = new CompoundButton[] {
                itemView.findViewById(R.id.day_button_0),
                itemView.findViewById(R.id.day_button_1),
                itemView.findViewById(R.id.day_button_2),
                itemView.findViewById(R.id.day_button_3),
                itemView.findViewById(R.id.day_button_4),
                itemView.findViewById(R.id.day_button_5),
                itemView.findViewById(R.id.day_button_6),
        };
        for (int i = 0; i < dayButtons.length; i++) {
            final CompoundButton dayButton = dayButtons[i];
            final int weekday = weekdays.get(i);
            dayButton.setText(UiDataModel.getUiDataModel().getShortWeekday(weekday));
            dayButton.setContentDescription(UiDataModel.getUiDataModel().getLongWeekday(weekday));
        }

        // Collapse handler
        itemView.setOnClickListener(v -> {
            Events.sendAlarmEvent(R.string.action_collapse_implied, R.string.label_deskclock);
            getItemHolder().collapse();
        });
        arrow.setOnClickListener(v -> {
            Events.sendAlarmEvent(R.string.action_collapse, R.string.label_deskclock);
            getItemHolder().collapse();
        });
        // Edit time handler
        clock.setOnClickListener(v ->
                getAlarmTimeClickHandler().onClockClicked(getItemHolder().item));
        // Edit label handler
        editLabel.setOnClickListener(v ->
                getAlarmTimeClickHandler().onEditLabelClicked(getItemHolder().item));
        // Vibrator checkbox handler
        vibrate.setOnClickListener(v ->
                getAlarmTimeClickHandler().setAlarmVibrationEnabled(getItemHolder().item,
                ((CheckBox) v).isChecked()));
        // Ringtone editor handler
        ringtone.setOnClickListener(v ->
                getAlarmTimeClickHandler().onRingtoneClicked(context, getItemHolder().item));
        // Delete alarm handler
        delete.setOnClickListener(v -> {
            getAlarmTimeClickHandler().onDeleteClicked(getItemHolder());
            v.announceForAccessibility(context.getString(R.string.alarm_deleted));
        });
        // Day buttons handler
        for (int i = 0; i < dayButtons.length; i++) {
            final int buttonIndex = i;
            dayButtons[i].setOnClickListener(view -> {
                final boolean isChecked = ((CompoundButton) view).isChecked();
                getAlarmTimeClickHandler().setDayOfWeekEnabled(getItemHolder().item,
                        isChecked, buttonIndex);
            });
        }

        itemView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onBindItemView(final AlarmItemHolder itemHolder) {
        super.onBindItemView(itemHolder);

        final Alarm alarm = itemHolder.item;
        final AlarmInstance alarmInstance = itemHolder.getAlarmInstance();
        final Context context = itemView.getContext();
        bindEditLabel(context, alarm);
        bindDaysOfWeekButtons(alarm, context);
        bindVibrator(alarm);
        bindRingtone(context, alarm);
        bindPreemptiveDismissButton(context, alarm, alarmInstance);
        bindRepeatText(context, alarm);
        bindAnnotations(alarm);
    }

    private void bindRingtone(Context context, Alarm alarm) {
        final String title = DataModel.getDataModel().getRingtoneTitle(alarm.alert);
        ringtone.setText(title);

        final String description = context.getString(R.string.ringtone_description);
        ringtone.setContentDescription(description + " " + title);

        final boolean silent = Utils.RINGTONE_SILENT.equals(alarm.alert);
        final Drawable icon = Utils.getVectorDrawable(context,
                silent ? R.drawable.ic_ringtone_silent : R.drawable.ic_ringtone);
        ringtone.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
    }

    private void bindDaysOfWeekButtons(Alarm alarm, Context context) {
        final List<Integer> weekdays = DataModel.getDataModel().getWeekdayOrder().getCalendarDays();
        for (int i = 0; i < weekdays.size(); i++) {
            final CompoundButton dayButton = dayButtons[i];
            if (alarm.daysOfWeek.isBitOn(weekdays.get(i))) {
                dayButton.setChecked(true);
                dayButton.setTextColor(ThemeUtils.resolveColor(context,
                        android.R.attr.windowBackground));
            } else {
                dayButton.setChecked(false);
                dayButton.setTextColor(Color.WHITE);
            }
        }
    }

    private void bindEditLabel(Context context, Alarm alarm) {
        editLabel.setText(alarm.label);
        editLabel.setContentDescription(alarm.label != null && alarm.label.length() > 0
                ? context.getString(R.string.label_description) + " " + alarm.label
                : context.getString(R.string.no_label_specified));
    }

    private void bindVibrator(Alarm alarm) {
        if (!mHasVibrator) {
            vibrate.setVisibility(View.INVISIBLE);
        } else {
            vibrate.setVisibility(View.VISIBLE);
            vibrate.setChecked(alarm.vibrate);
        }
    }

    private void bindAnnotations(Alarm alarm) {
        annotationsAlpha = alarm.enabled ? CLOCK_ENABLED_ALPHA : CLOCK_DISABLED_ALPHA;
        setChangingViewsAlpha(annotationsAlpha);
    }

    private AlarmTimeClickHandler getAlarmTimeClickHandler() {
        return getItemHolder().getAlarmTimeClickHandler();
    }

    @Override
    public Animator onAnimateChange(List<Object> payloads, int fromLeft, int fromTop, int fromRight,
                                    int fromBottom, long duration) {
        /* There are no possible partial animations for expanded view holders. */
        return null;
    }

    @Override
    public Animator onAnimateChange(final ViewHolder oldHolder, ViewHolder newHolder,
                                    long duration) {
        if (!(oldHolder instanceof AlarmItemViewHolder)
                || !(newHolder instanceof AlarmItemViewHolder)) {
            return null;
        }

        final boolean isExpanding = this == newHolder;
        AnimatorUtils.setBackgroundAlpha(itemView, isExpanding ? 0 : 255);
        setChangingViewsAlpha(isExpanding ? 0f : annotationsAlpha);

        final Animator changeAnimatorSet = isExpanding
                ? createExpandingAnimator((AlarmItemViewHolder) oldHolder, duration)
                : createCollapsingAnimator((AlarmItemViewHolder) newHolder, duration);
        changeAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                AnimatorUtils.setBackgroundAlpha(itemView, 255);
                arrow.setTranslationY(0f);
                setChangingViewsAlpha(annotationsAlpha);
                arrow.jumpDrawablesToCurrentState();
                arrow.setVisibility(isExpanding ? View.VISIBLE : View.INVISIBLE);
                clock.setVisibility(isExpanding ? View.VISIBLE : View.INVISIBLE);
                onOff.setVisibility(isExpanding ? View.VISIBLE : View.INVISIBLE);
                ellipsizeLayout.setVisibility(isExpanding ? View.VISIBLE : View.INVISIBLE);
            }
        });
        return changeAnimatorSet;
    }

    private Animator createCollapsingAnimator(AlarmItemViewHolder newHolder, long duration) {
        final boolean daysVisible = repeatDays.getVisibility() == View.VISIBLE;
        final int numberOfItems = countNumberOfItems();

        final Animator backgroundAnimator = ObjectAnimator.ofPropertyValuesHolder(itemView,
                PropertyValuesHolder.ofInt(AnimatorUtils.BACKGROUND_ALPHA, 255, 0));
        backgroundAnimator.setDuration(duration);

        final Animator boundsAnimator = getBoundsAnimator(itemView, newHolder.itemView, duration);
        final Animator switchAnimator = getBoundsAnimator(onOff, newHolder.onOff, duration);
        final Animator clockAnimator = getBoundsAnimator(clock, newHolder.clock, duration);
        final Animator ellipseAnimator = getBoundsAnimator(ellipsizeLayout,
                newHolder.ellipsizeLayout, duration);

        final long shortDuration = (long) (duration * ANIM_SHORT_DURATION_MULTIPLIER);
        final Animator editLabelAnimation = ObjectAnimator.ofFloat(editLabel, View.ALPHA, 0f)
                .setDuration(shortDuration);
        final Animator repeatDaysAnimation = ObjectAnimator.ofFloat(repeatDays, View.ALPHA, 0f)
                .setDuration(shortDuration);
        final Animator vibrateAnimation = ObjectAnimator.ofFloat(vibrate, View.ALPHA, 0f)
                .setDuration(shortDuration);
        final Animator ringtoneAnimation = ObjectAnimator.ofFloat(ringtone, View.ALPHA, 0f)
                .setDuration(shortDuration);
        final Animator dismissAnimation = ObjectAnimator.ofFloat(preemptiveDismissButton,
                View.ALPHA, 0f).setDuration(shortDuration);
        final Animator deleteAnimation = ObjectAnimator.ofFloat(delete, View.ALPHA, 0f)
                .setDuration(shortDuration);

        // Set the staggered delays; use the first portion (duration * (1 - 1/4 - 1/6)) of the time,
        // so that the final animation, with a duration of 1/4 the total duration, finishes exactly
        // before the collapsed holder begins expanding.
        long startDelay = 0L;
        final long delayIncrement = (long) (duration * ANIM_LONG_DELAY_INCREMENT_MULTIPLIER)
                / (numberOfItems - 1);
        deleteAnimation.setStartDelay(startDelay);
        if (preemptiveDismissButton.getVisibility() == View.VISIBLE) {
            startDelay += delayIncrement;
            dismissAnimation.setStartDelay(startDelay);
        }
        startDelay += delayIncrement;
        editLabelAnimation.setStartDelay(startDelay);
        startDelay += delayIncrement;
        vibrateAnimation.setStartDelay(startDelay);
        ringtoneAnimation.setStartDelay(startDelay);
        startDelay += delayIncrement;
        if (daysVisible) {
            repeatDaysAnimation.setStartDelay(startDelay);
            startDelay += delayIncrement;
        }

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(backgroundAnimator, boundsAnimator,
                repeatDaysAnimation, vibrateAnimation, ringtoneAnimation, editLabelAnimation,
                deleteAnimation, dismissAnimation, switchAnimator, clockAnimator, ellipseAnimator);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                newHolder.clock.setVisibility(View.INVISIBLE);
                newHolder.onOff.setVisibility(View.INVISIBLE);
                newHolder.arrow.setVisibility(View.INVISIBLE);
                newHolder.ellipsizeLayout.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                newHolder.clock.setVisibility(View.VISIBLE);
                newHolder.onOff.setVisibility(View.VISIBLE);
                newHolder.arrow.setVisibility(View.VISIBLE);
                newHolder.ellipsizeLayout.setVisibility(View.VISIBLE);
            }
        });
        return animatorSet;
    }

    private Animator createExpandingAnimator(AlarmItemViewHolder oldHolder, long duration) {
        final View oldView = oldHolder.itemView;
        final View newView = itemView;
        final Animator boundsAnimator = AnimatorUtils.getBoundsAnimator(newView, oldView, newView);
        boundsAnimator.setDuration(duration);
        boundsAnimator.setInterpolator(AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN);

        final Animator backgroundAnimator = ObjectAnimator.ofPropertyValuesHolder(newView,
                PropertyValuesHolder.ofInt(AnimatorUtils.BACKGROUND_ALPHA, 0, 255));
        backgroundAnimator.setDuration(duration);

        final long longDuration = (long) (duration * ANIM_LONG_DURATION_MULTIPLIER);
        final Animator repeatDaysAnimation = ObjectAnimator.ofFloat(repeatDays, View.ALPHA, 1f)
                .setDuration(longDuration);
        final Animator ringtoneAnimation = ObjectAnimator.ofFloat(ringtone, View.ALPHA, 1f)
                .setDuration(longDuration);
        final Animator dismissAnimation = ObjectAnimator.ofFloat(preemptiveDismissButton,
                View.ALPHA, 1f).setDuration(longDuration);
        final Animator vibrateAnimation = ObjectAnimator.ofFloat(vibrate, View.ALPHA, 1f)
                .setDuration(longDuration);
        final Animator editLabelAnimation = ObjectAnimator.ofFloat(editLabel, View.ALPHA, 1f)
                .setDuration(longDuration);
        final Animator deleteAnimation = ObjectAnimator.ofFloat(delete, View.ALPHA, 1f)
                .setDuration(longDuration);
        final Animator arrowAnimation = ObjectAnimator.ofFloat(arrow, View.TRANSLATION_Y, 0f)
                .setDuration(duration);
        arrowAnimation.setInterpolator(AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN);

        // Set the stagger delays; delay the first by the amount of time it takes for the collapse
        // to complete, then stagger the expansion with the remaining time.
        long startDelay = (long) (duration * ANIM_STANDARD_DELAY_MULTIPLIER);
        final int numberOfItems = countNumberOfItems();
        final long delayIncrement = (long) (duration * ANIM_SHORT_DELAY_INCREMENT_MULTIPLIER)
                / (numberOfItems - 1);
        final boolean daysVisible = repeatDays.getVisibility() == View.VISIBLE;
        if (daysVisible) {
            repeatDaysAnimation.setStartDelay(startDelay);
            startDelay += delayIncrement;
        }
        ringtoneAnimation.setStartDelay(startDelay);
        vibrateAnimation.setStartDelay(startDelay);
        startDelay += delayIncrement;
        editLabelAnimation.setStartDelay(startDelay);
        startDelay += delayIncrement;
        if (preemptiveDismissButton.getVisibility() == View.VISIBLE) {
            dismissAnimation.setStartDelay(startDelay);
            startDelay += delayIncrement;
        }
        deleteAnimation.setStartDelay(startDelay);

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(backgroundAnimator, boundsAnimator,
                repeatDaysAnimation, vibrateAnimation, ringtoneAnimation, editLabelAnimation,
                deleteAnimation, dismissAnimation, arrowAnimation);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                AnimatorUtils.startDrawableAnimation(arrow);
            }
        });
        return animatorSet;
    }

    private int countNumberOfItems() {
        // Always between 4 and 6 items.
        int numberOfItems = 4;
        if (preemptiveDismissButton.getVisibility() == View.VISIBLE) {
            numberOfItems++;
        }
        if (repeatDays.getVisibility() == View.VISIBLE) {
            numberOfItems++;
        }
        return numberOfItems;
    }

    private void setChangingViewsAlpha(float alpha) {
        editLabel.setAlpha(alpha);
        repeatDays.setAlpha(alpha);
        preemptiveDismissButton.setAlpha(alpha);
        daysOfWeek.setAlpha(alpha);
    }

    public static class Factory implements ItemAdapter.ItemViewHolder.Factory {

        private final LayoutInflater mLayoutInflater;
        private final boolean mHasVibrator;

        public Factory(Context context) {
            mLayoutInflater = LayoutInflater.from(context);
            mHasVibrator = (context.getSystemService(Vibrator.class)).hasVibrator();
        }

        @Override
        public ItemAdapter.ItemViewHolder<?> createViewHolder(ViewGroup parent, int viewType) {
            final View itemView = mLayoutInflater.inflate(viewType, parent, false);
            return new ExpandedAlarmViewHolder(itemView, mHasVibrator);
        }
    }
}
