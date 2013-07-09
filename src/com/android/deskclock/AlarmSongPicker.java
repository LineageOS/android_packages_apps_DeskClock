package com.android.deskclock;

import java.io.FileDescriptor;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

		final ArrayAdapter<Song> mAdpater = new SongListAdapter(this,
				R.layout.alarm_song_picker_item_row, mSongList);
		mListView.setAdapter(mAdpater);

		mListView.setOnItemClickListener(new OnItemClickListener() {

			Song lastSelectedSong = null;

			@Override
			public void onItemClick(AdapterView<?> arg0, View view,
					int position, long arg3) {

				Song selectedSong = mSongList.get(position);

				mSelectedSong = selectedSong;

				if (selectedSong != null) {
					playMusic(selectedSong.mPath);
					selectedSong.isPlaying = true;
				}

				if (lastSelectedSong != null) {
					lastSelectedSong.isPlaying = false;
				}

				if (selectedSong == lastSelectedSong) {
					if (selectedSong.isPlaying) {
						stopMusic();
					} else {
						playMusic(selectedSong.mPath);
						selectedSong.isPlaying = true;
					}
				}

				lastSelectedSong = selectedSong;

				mAdpater.notifyDataSetChanged();

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
		if (mPlayer.isPlaying())
			mPlayer.stop();
	}

	private List<Song> getMusic() {
		final Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

		final String[] cols = { MediaStore.Audio.Media._ID,
				MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.TITLE,
				MediaStore.Audio.Media.DURATION,
				MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DATA };
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
				int duration = cursor
						.getInt(cursor
								.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));

				Date date = new Date(duration);
				SimpleDateFormat formatter = new SimpleDateFormat("mm:ss",
						Locale.getDefault());
				String durationString = formatter.format(date);

				String album_id = cursor
						.getString(cursor
								.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));

				String path = cursor.getString(cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));

				Song musicItem = new Song(title, artist, durationString,
						album_id, path);

				Bitmap albumArt = getAlbumArt(album_id);
				musicItem.mAlbumArt = albumArt;

				songs.add(musicItem);
			} while (cursor.moveToNext());
		}

		cursor.close();

		return songs;
	}

	private Bitmap getAlbumArt(String albumId) {

		Bitmap bm = null;
		try {
			final Uri sArtworkUri = Uri
					.parse("content://media/external/audio/albumart");

			Uri uri = ContentUris.withAppendedId(sArtworkUri,
					Long.parseLong(albumId));

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
				ListView lView = (ListView) findViewById(R.id.alarm_song_picker_listview);
				Song song = (Song) lView.getItemAtPosition(lView
						.getCheckedItemPosition());
				Intent resultIntent = new Intent();
				resultIntent.putExtra(AlarmSongPicker.EXTRA_SELECTED_SONG_URI,
						Uri.parse(song.mPath));
				resultIntent.putExtra(
						AlarmSongPicker.EXTRA_SELECTED_SONG_TITLE, song.mTitle);
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

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return super.onCreateOptionsMenu(menu);
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
		public String mAlbumID;
		public String mPath;

		public Bitmap mAlbumArt;

		public boolean isPlaying = false;

		public Song(String title, String artist, String duration,
				String album_id, String path) {
			mArtist = artist;
			mDuration = duration;
			mTitle = title;
			mAlbumID = album_id;
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
			public ImageView album_art;
			public ImageView now_playing;
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
				holder.album_art = (ImageView) view
						.findViewById(R.id.alarm_song_picker_item_image);
				holder.now_playing = (ImageView) view
						.findViewById(R.id.alarm_song_picker_item_play);
				view.setTag(holder);
			} else
				holder = (ViewHolder) view.getTag();

			final Song song = mSongs.get(position);

			if (song != null) {
				holder.title.setText(song.mTitle);
				holder.text.setText(song.mArtist + " - " + song.mDuration);

				if (song.mAlbumArt != null)
					holder.album_art.setImageBitmap(song.mAlbumArt);
				else
					holder.album_art.setImageResource(R.drawable.ic_ringtone);

				if (song.isPlaying)
					holder.now_playing.setImageResource(R.drawable.ic_ringtone);
				else
					holder.now_playing
							.setImageResource(android.R.color.transparent);
			}

			return view;
		}

	}
}
