/*
 * Copyright (C) 2012-2013 The CyanogenMod Project
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

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.v4.widget.CursorAdapter;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Formatter;

public class AlarmSongPicker extends Activity implements LoaderManager.LoaderCallbacks<Cursor>, OnQueryTextListener{
    public final static String EXTRA_SELECTED_SONG_URI = "selected_music_uri";

    private MediaPlayer mPlayer;

    private SongCursorAdapter mAdapter;
    private Uri mSelectedSongUri = Uri.EMPTY;
    private long mNowPlayingId = -1;

    private String mSearchFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alarm_song_picker);

        mPlayer = new MediaPlayer();

        final ListView mListView = (ListView) findViewById(R.id.alarm_song_picker_listview);

        mAdapter = new SongCursorAdapter(this, null, 0);

        mListView.setAdapter(mAdapter);

        getLoaderManager().initLoader(0, null, this);

        mListView.setOnItemClickListener(new OnItemClickListener() {

            Uri lastSelectedSongUri = Uri.EMPTY;
            long selectedSongId = -1;

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {

                Cursor cursor = ((SongCursorAdapter) parent.getAdapter()).getCursor();
                cursor.moveToPosition(position);
                mSelectedSongUri = Uri.parse(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA)));
                selectedSongId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));

                if (mSelectedSongUri != Uri.EMPTY) {
                    playMusic(mSelectedSongUri);
                    mNowPlayingId = selectedSongId;
                }

                if (mSelectedSongUri == lastSelectedSongUri) {
                    if (selectedSongId == mNowPlayingId) {
                        stopMusic();
                    } else {
                        playMusic(mSelectedSongUri);
                        mNowPlayingId = selectedSongId;
                    }
                }

                lastSelectedSongUri = mSelectedSongUri;

                mAdapter.notifyDataSetChanged();
            }
        });

        setUpBottomButtons();
    }

    @Override
    protected void onPause() {
        mPlayer.reset();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater mi = getMenuInflater();
        mi.inflate(R.menu.alarm_song_picker_menu, menu);
        MenuItem item = menu.findItem(R.id.alarm_song_picker_menu_search);
        SearchView sv = (SearchView) item.getActionView();
        sv.setOnQueryTextListener(this);
        return true;
    }

    protected void playMusic(Uri path) {

        stopMusic();

        try {
            mPlayer.reset();
            mPlayer.setDataSource(path.toString());
            mPlayer.prepare();
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void stopMusic() {
        if (mPlayer.isPlaying()) {
            mPlayer.stop();
        }
    }

    public static String makeTimeString(Context context, long secs) {
        final StringBuilder formatBuilder = new StringBuilder();
        Formatter formatter = new Formatter(formatBuilder, Locale.getDefault());

        String durationformat = context.getString(secs < 3600 ? R.string.durationformatshort
                : R.string.durationformatlong);

        formatBuilder.setLength(0);

        final Object[] timeArgs = new Object[5];
        timeArgs[0] = secs / 3600;
        timeArgs[1] = secs / 60;
        timeArgs[2] = secs / 60 % 60;
        timeArgs[3] = secs;
        timeArgs[4] = secs % 60;

        return formatter.format(durationformat, timeArgs).toString();
    }

    private Bitmap getAlbumArt(long albumId) {

        Bitmap bm = null;
        try {
            final Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");

            Uri uri = ContentUris.withAppendedId(sArtworkUri, albumId);

            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");

            if (pfd != null) {
                FileDescriptor fd = pfd.getFileDescriptor();
                bm = decodeSampledBitmapFromResource(fd, 100, 100);

            }
        } catch (Exception e) {
        }
        return bm;
    }

    public static Bitmap decodeSampledBitmapFromResource(FileDescriptor fd,
            int reqWidth, int reqHeight) {

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd, null, options);
    }

    public static int calculateInSampleSize(BitmapFactory.Options options,
            int reqWidth, int reqHeight) {

        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }

        return inSampleSize;
    }

    private void setUpBottomButtons() {

        Button okButton = (Button) findViewById(R.id.alarm_song_picker_ok_button);
        okButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra(AlarmSongPicker.EXTRA_SELECTED_SONG_URI, mSelectedSongUri);
                setResult(RESULT_OK, resultIntent);
                finish();
                return;
            }
        });
        Button cancelButton = (Button) findViewById(R.id.alarm_song_picker_cancel_button);
        cancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
        });
    }

    public class SongCursorAdapter extends CursorAdapter {

        public SongCursorAdapter(Context context, Cursor c, int flags) {
            super(context, c, flags);
        }

        public class ViewHolder {
            public TextView title;
            public TextView text;
            public ImageView albumArt;
            public ImageView nowPlaying;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder holder = (ViewHolder) view.getTag();

            long songId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
            String title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
            String artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
            long duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
            long albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
            String durationString = makeTimeString(getBaseContext(), duration/1000);
            Bitmap albumArt = getAlbumArt(albumId);

            holder.title.setText(title);
            holder.text.setText(artist + " - " + durationString);

            if (albumArt != null) {
                holder.albumArt.setImageBitmap(albumArt);
            } else {
                holder.albumArt.setImageResource(R.drawable.ic_ringtone);
            }

            if(songId == mNowPlayingId){
                holder.nowPlaying.setImageResource(R.drawable.ic_ringtone);
            } else {
                holder.nowPlaying.setImageResource(android.R.color.transparent);
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.alarm_song_picker_item_row,
                    null);
            ViewHolder holder = new ViewHolder();

            holder.title = (TextView) rowView
                    .findViewById(R.id.alarm_song_picker_item_title);
            holder.text = (TextView) rowView
                    .findViewById(R.id.alarm_song_picker_item_text);
            holder.albumArt = (ImageView) rowView
                    .findViewById(R.id.alarm_song_picker_item_image);
            holder.nowPlaying = (ImageView) rowView
                    .findViewById(R.id.alarm_song_picker_item_play);
            rowView.setTag(holder);
            return rowView;
        }

    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        final String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DATA
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + "=1";
        if(mSearchFilter != null) {
            selection += " AND " + MediaStore.Audio.Media.TITLE + " LIKE '%" + mSearchFilter + "%'";
        }
        return new CursorLoader(getApplication(), uri, projection, selection, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
        stopMusic();
        mSelectedSongUri = Uri.EMPTY;
        mNowPlayingId = -1;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mSearchFilter = !TextUtils.isEmpty(newText) ? newText : null;
        getLoaderManager().restartLoader(0, null, this);
        return false;
    }
}
