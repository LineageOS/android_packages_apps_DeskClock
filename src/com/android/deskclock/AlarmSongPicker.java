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
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Formatter;

public class AlarmSongPicker extends Activity {
    public final static String EXTRA_SELECTED_SONG_URI = "selected_music_uri";
    public final static String EXTRA_SELECTED_SONG_TITLE = "selected_song_title";

    private List<Song> mSongList;
    private MediaPlayer mPlayer;
    private Song mSelectedSong;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alarm_song_picker);

        mPlayer = new MediaPlayer();

        final ListView mListView = (ListView) findViewById(R.id.alarm_song_picker_listview);

        mSongList = getMusic();

        final ArrayAdapter<Song> mAdapter = new SongListAdapter(this,
                R.layout.alarm_song_picker_item_row, mSongList);
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new OnItemClickListener() {

            Song lastSelectedSong = null;

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {

                mSelectedSong = mSongList.get(position);

                if (mSelectedSong != null) {
                    playMusic(mSelectedSong.mPath);
                    mSelectedSong.mIsPlaying = true;
                }

                if (lastSelectedSong != null) {
                    lastSelectedSong.mIsPlaying = false;
                }

                if (mSelectedSong == lastSelectedSong) {
                    if (mSelectedSong.mIsPlaying) {
                        stopMusic();
                    } else {
                        playMusic(mSelectedSong.mPath);
                        mSelectedSong.mIsPlaying = true;
                    }
                }

                lastSelectedSong = mSelectedSong;

                mAdapter.notifyDataSetChanged();
            }
        });

        setUpBottomButtons();
    }

    protected void playMusic(String path) {

        stopMusic();

        try {
            mPlayer.reset();
            mPlayer.setDataSource(path);
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

    private List<Song> getMusic() {
        final Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        final String[] cols = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DATA
        };
        final String where = MediaStore.Audio.Media.IS_MUSIC + "=1";
        final Cursor cursor = getContentResolver().query(uri, cols, where,
                null, null);

        List<Song> songs = new ArrayList<Song>();

        if (cursor.moveToFirst()) {
            do {

                String title = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                String artist = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                long duration = cursor.getLong(cursor
                                .getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
                String durationString = makeTimeString(getBaseContext(), duration/1000);

                long albumId = cursor.getLong(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));

                String path = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));

                Song musicItem = new Song(title, artist, durationString, albumId, path);

                musicItem.mAlbumArt = getAlbumArt(albumId);

                songs.add(musicItem);
            } while (cursor.moveToNext());
        }

        cursor.close();

        return songs;
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
            final Uri sArtworkUri = Uri
                    .parse("content://media/external/audio/albumart");

            Uri uri = ContentUris.withAppendedId(sArtworkUri, albumId);

            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(
                    uri, "r");

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

        options.inSampleSize = calculateInSampleSize(options, reqWidth,
                reqHeight);

        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd, null, options);
    }

    public static int calculateInSampleSize(BitmapFactory.Options options,
            int reqWidth, int reqHeight) {

        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int heightRatio = Math.round((float) height
                    / (float) reqHeight);
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
                if(mSelectedSong != null) {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra(AlarmSongPicker.EXTRA_SELECTED_SONG_URI,
                        Uri.parse(mSelectedSong.mPath));
                    resultIntent.putExtra(
                        AlarmSongPicker.EXTRA_SELECTED_SONG_TITLE, mSelectedSong.mTitle);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                }
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

    @Override
    protected void onPause() {
        mPlayer.reset();
        super.onPause();
    }

    public class Song {
        public String mTitle;
        public String mArtist;
        public String mDuration;
        public long mAlbumId;
        public String mPath;

        public Bitmap mAlbumArt;

        public boolean mIsPlaying = false;

        public Song(String title, String artist, String duration,
                long albumId, String path) {
            mArtist = artist;
            mDuration = duration;
            mTitle = title;
            mAlbumId = albumId;
            mPath = path;
        }
    }

    public class SongListAdapter extends ArrayAdapter<Song> {

        private List<Song> mSongs;

        public SongListAdapter(Context context, int resource, List<Song> songs) {
            super(context, resource, songs);
            mSongs = songs;
        }

        public class ViewHolder {
            public TextView title;
            public TextView text;
            public ImageView albumArt;
            public ImageView nowPlaying;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            View view = convertView;
            ViewHolder holder;

            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) getContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.alarm_song_picker_item_row,
                        null);
                holder = new ViewHolder();
                holder.title = (TextView) view
                        .findViewById(R.id.alarm_song_picker_item_title);
                holder.text = (TextView) view
                        .findViewById(R.id.alarm_song_picker_item_text);
                holder.albumArt = (ImageView) view
                        .findViewById(R.id.alarm_song_picker_item_image);
                holder.nowPlaying = (ImageView) view
                        .findViewById(R.id.alarm_song_picker_item_play);
                view.setTag(holder);
            } else
                holder = (ViewHolder) view.getTag();

            final Song song = mSongs.get(position);

            if (song != null) {
                holder.title.setText(song.mTitle);
                holder.text.setText(song.mArtist + " - " + song.mDuration);

                if (song.mAlbumArt != null) {
                    holder.albumArt.setImageBitmap(song.mAlbumArt);
                } else {
                    holder.albumArt.setImageResource(R.drawable.ic_ringtone);
                }

                if (song.mIsPlaying) {
                    holder.nowPlaying.setImageResource(R.drawable.ic_ringtone);
                } else {
                    holder.nowPlaying
                            .setImageResource(android.R.color.transparent);
                }
            }

            return view;
        }

    }
}
