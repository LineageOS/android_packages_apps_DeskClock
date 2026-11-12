/*
 * Copyright (C) 2026 The LineageOS Project
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

package com.android.deskclock.alarms;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.android.deskclock.ItemAdapter;
import com.android.deskclock.R;
import com.android.deskclock.ThemeUtils;
import com.android.deskclock.Utils;
import com.android.deskclock.alarms.dataadapter.AlarmItemHolder;

/**
 * Deletes an alarm when its card is swiped left or right, through the same path as the
 * delete button so the Undo snackbar still appears.
 */
public final class SwipeToDeleteCallback extends ItemTouchHelper.SimpleCallback {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Drawable mIcon;
    private final float mCornerRadius;
    private final int mIconMargin;

    public SwipeToDeleteCallback(Context context) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        mPaint.setColor(ThemeUtils.resolveColor(context, R.attr.colorErrorContainer));
        mIcon = Utils.getVectorDrawable(context, R.drawable.ic_delete);
        mIcon.setTint(ThemeUtils.resolveColor(context, R.attr.colorOnErrorContainer));
        mCornerRadius = context.getResources().getDimension(R.dimen.alarm_card_corner_radius);
        mIconMargin = context.getResources()
                .getDimensionPixelSize(R.dimen.alarm_horizontal_padding);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder,
            @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder) {
        return holderOf(viewHolder) == null ? 0 : super.getSwipeDirs(recyclerView, viewHolder);
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.4f;
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return defaultValue / 2;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        final AlarmItemHolder holder = holderOf(viewHolder);
        if (holder != null) {
            holder.getAlarmTimeClickHandler().onDeleteClicked(holder);
            viewHolder.itemView.announceForAccessibility(
                    viewHolder.itemView.getContext().getString(R.string.alarm_deleted));
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState,
            boolean isCurrentlyActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0) {
            final View item = viewHolder.itemView;
            // Fade with the card as it is removed.
            final int alpha = Math.round(255 * item.getAlpha());
            mRect.set(item.getLeft(), item.getTop(), item.getRight(), item.getBottom());
            mPaint.setAlpha(alpha);
            c.drawRoundRect(mRect, mCornerRadius, mCornerRadius, mPaint);

            final int size = mIcon.getIntrinsicHeight();
            final int top = item.getTop() + (item.getHeight() - size) / 2;
            final int left = dX > 0
                    ? item.getLeft() + mIconMargin
                    : item.getRight() - mIconMargin - size;
            mIcon.setBounds(left, top, left + size, top + size);
            final float reveal = Math.abs(dX) / (mIconMargin * 2f + size);
            mIcon.setAlpha(Math.round(alpha * Math.min(1f, reveal)));
            mIcon.draw(c);
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    private static AlarmItemHolder holderOf(RecyclerView.ViewHolder viewHolder) {
        if (viewHolder instanceof ItemAdapter.ItemViewHolder) {
            final Object item = ((ItemAdapter.ItemViewHolder<?>) viewHolder).getItemHolder();
            if (item instanceof AlarmItemHolder) {
                return (AlarmItemHolder) item;
            }
        }
        return null;
    }
}
