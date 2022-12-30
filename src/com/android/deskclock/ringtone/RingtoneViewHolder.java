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

package com.android.deskclock.ringtone;

import static android.view.View.GONE;
import static android.view.View.OnClickListener;
import static android.view.View.VISIBLE;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.android.deskclock.AnimatorUtils;
import com.android.deskclock.ItemAdapter;
import com.android.deskclock.R;
import com.android.deskclock.Utils;

final class RingtoneViewHolder extends ItemAdapter.ItemViewHolder<RingtoneHolder>
        implements OnClickListener, PopupMenu.OnMenuItemClickListener {

    static final int VIEW_TYPE_SYSTEM_SOUND = R.layout.ringtone_item_sound;
    static final int VIEW_TYPE_CUSTOM_SOUND = -R.layout.ringtone_item_sound;
    static final int CLICK_NORMAL = 0;
    static final int CLICK_REMOVE = -1;
    static final int CLICK_NO_PERMISSIONS = -2;

    private final View mSelectedView;
    private final TextView mNameView;
    private final ImageView mImageView;
    private final ImageView mMenuView;

    private RingtoneViewHolder(View itemView) {
        super(itemView);
        itemView.setOnClickListener(this);

        mSelectedView = itemView.findViewById(R.id.sound_image_selected);
        mNameView = itemView.findViewById(R.id.ringtone_name);
        mImageView = itemView.findViewById(R.id.ringtone_image);
        mMenuView = itemView.findViewById(R.id.music_actions);
    }

    @Override
    protected void onBindItemView(RingtoneHolder itemHolder) {
        mNameView.setText(itemHolder.getName());
        final boolean opaque = itemHolder.isSelected() || !itemHolder.hasPermissions();
        mNameView.setAlpha(opaque ? 1f : .63f);
        mImageView.setAlpha(opaque ? 1f : .63f);
        mImageView.clearColorFilter();

        final int itemViewType = getItemViewType();
        if (itemViewType == VIEW_TYPE_CUSTOM_SOUND) {
            if (!itemHolder.hasPermissions()) {
                mImageView.setImageResource(R.drawable.ic_error_red);
            } else {
                mImageView.setImageResource(R.drawable.ic_ringtone);
            }
        } else if (itemHolder.item == Utils.RINGTONE_SILENT) {
            mImageView.setImageResource(R.drawable.ic_ringtone_silent);
        } else {
            mImageView.setImageResource(R.drawable.ic_ringtone);
        }
        AnimatorUtils.startDrawableAnimation(mImageView);

        mSelectedView.setVisibility(itemHolder.isSelected() ? VISIBLE : GONE);

        final int bgColorId = itemHolder.isSelected() ? R.color.white_08p : R.color.transparent;
        itemView.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), bgColorId));

        if (itemViewType == VIEW_TYPE_CUSTOM_SOUND) {
            mMenuView.setVisibility(VISIBLE);
            mMenuView.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(v.getContext(), v);
                popupMenu.getMenuInflater().inflate(R.menu.ringtone_item_menu, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(this);
                popupMenu.show();
            });
        }
    }

    @Override
    public void onClick(View view) {
        if (getItemHolder().hasPermissions()) {
            notifyItemClicked(RingtoneViewHolder.CLICK_NORMAL);
        } else {
            notifyItemClicked(RingtoneViewHolder.CLICK_NO_PERMISSIONS);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.remove) {
            notifyItemClicked(RingtoneViewHolder.CLICK_REMOVE);
            return true;
        }
        return false;
    }

    public static class Factory implements ItemAdapter.ItemViewHolder.Factory {

        private final LayoutInflater mInflater;

        Factory(LayoutInflater inflater) {
            mInflater = inflater;
        }

        @Override
        public ItemAdapter.ItemViewHolder<?> createViewHolder(ViewGroup parent, int viewType) {
            final View itemView = mInflater.inflate(R.layout.ringtone_item_sound, parent, false);
            return new RingtoneViewHolder(itemView);
        }
    }
}
