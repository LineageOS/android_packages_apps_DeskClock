/*
 * Copyright (C) 2014 The CyanogenMod Project
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

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;

import java.io.IOException;
import java.util.Map;

public class AlarmMediaPlayer extends MediaPlayer {

    public static final Uri RANDOM_URI = Uri.parse("random");

    private final Context mContext;

    public AlarmMediaPlayer(Context context) {
        mContext = context;
    }

    @Override
    public void setDataSource(Context context, Uri uri) throws IOException,
            IllegalArgumentException, SecurityException, IllegalStateException {
        setDataSource(context, uri, null);
    }

    @Override
    public void setDataSource(Context context, Uri uri, Map<String, String> headers)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        if (uri.equals(RANDOM_URI)) {
            super.setDataSource(context, getNextRandomUri(), headers);
        } else {
            super.setDataSource(context, uri, headers);
        }
    }

    private Uri getNextRandomUri() {
        Cursor c = mContext.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null, null, null, "RANDOM() LIMIT 1");
        try {
            if (c.moveToFirst()) {
                long id = c.getLong(c.getColumnIndex(MediaStore.Audio.Media._ID));
                return ContentUris.withAppendedId(Audio.Media.EXTERNAL_CONTENT_URI, id);
            }
        } finally {
            c.close();
        }
        return null;
    }
}
