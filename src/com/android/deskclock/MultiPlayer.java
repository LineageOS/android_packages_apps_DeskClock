package com.android.deskclock;

import java.io.FileDescriptor;
import java.io.IOException;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;

public class MultiPlayer implements MediaPlayer.OnCompletionListener {

    public static final Uri RANDOM_URI = Uri.parse("random");

    private MediaPlayer mCurrentMediaPlayer = new MediaPlayer();
    private MediaPlayer mNextMediaPlayer;

    private boolean mIsInitialized = false;

    private Context mContext;

    private boolean mSingle;

    private OnErrorListener mErrorListener;
    private int mAudioStreamType = AudioManager.STREAM_ALARM;
    private boolean mLooping;
    private boolean mIsExternal;
    private boolean mRandom;

    private Cursor mCursor;

    private int mColumnIndex;
    private float mLeftVolume = -1;
    private float mRightVolume = -1;

    /**
     * Constructor of <code>MultiPlayer</code>
     */
    public MultiPlayer(Context context) {
        mContext = context;
    }

    /**
     * @param uri The Uri you want to play
     */
    public void setDataSource(Context context, final Uri uri) {
        handleSetDataSourceUri(uri);
        mIsInitialized = setDataSourceImpl(mCurrentMediaPlayer, getNextUri());
        if (mIsInitialized) {
            if (mSingle) {
                setNextDataSource(null);
            } else {
                setNextDataSource(getNextUri());
            }
        }
    }

    /**
     * Sets the data source (FileDescriptor) to use. It is the caller's responsibility
     * to close the file descriptor. It is safe to do so as soon as this call returns.
     *
     * @param fd the FileDescriptor for the file you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void setDataSource(FileDescriptor fd, long offset, long length) {
        mIsInitialized = setDataSourceImpl(mCurrentMediaPlayer, fd, offset, length);
        if (mIsInitialized) {
            mSingle = true;
            setNextDataSource(null);
        }
    }

    /**
     * @param player The {@link MediaPlayer} to use
     * @param uri The path of the file, or the http/rtsp URL of the stream
     *            you want to play
     * @return True if the <code>player</code> has been prepared and is
     *         ready to play, false otherwise
     */
    private boolean setDataSourceImpl(final MediaPlayer player, final Uri uri) {
        try {
            player.reset();
            player.setOnPreparedListener(null);
            player.setDataSource(mContext, uri);
            player.setAudioStreamType(mAudioStreamType);
            if (mLeftVolume != -1 && mRightVolume != -1) {
                player.setVolume(mLeftVolume, mRightVolume);
            }
            mCurrentMediaPlayer.setLooping(mLooping);
            player.prepare();
        } catch (final IOException todo) {
            return false;
        } catch (final IllegalArgumentException todo) {
            return false;
        }
        player.setOnCompletionListener(this);
        player.setOnErrorListener(mErrorListener);
        return true;
    }

    private boolean setDataSourceImpl(
            final MediaPlayer player, FileDescriptor fd, long offset, long length) {
        try {
            player.reset();
            player.setOnPreparedListener(null);
            player.setDataSource(fd, offset, length);
            player.setAudioStreamType(mAudioStreamType);
            if (mLeftVolume != -1 && mRightVolume != -1) {
                player.setVolume(mLeftVolume, mRightVolume);
            }
            player.setLooping(mLooping);
            player.prepare();
        } catch (final IOException todo) {
            return false;
        } catch (final IllegalArgumentException todo) {
            return false;
        }
        player.setOnCompletionListener(this);
        player.setOnErrorListener(mErrorListener);
        return true;
    }

    /**
     * Set the MediaPlayer to start when this MediaPlayer finishes playback.
     *
     * @param uri The path of the file, or the http/rtsp URL of the stream
     *            you want to play
     */
    public void setNextDataSource(final Uri uri) {
        mCurrentMediaPlayer.setNextMediaPlayer(null);
        if (mNextMediaPlayer != null) {
            mNextMediaPlayer.release();
            mNextMediaPlayer = null;
        }
        if (uri == null) {
            return;
        }
        mNextMediaPlayer = new MediaPlayer();
        mNextMediaPlayer.setAudioSessionId(getAudioSessionId());
        if (setDataSourceImpl(mNextMediaPlayer, uri)) {
            mCurrentMediaPlayer.setNextMediaPlayer(mNextMediaPlayer);
        } else {
            if (mNextMediaPlayer != null) {
                mNextMediaPlayer.release();
                mNextMediaPlayer = null;
            }
        }
    }

    /**
     * Starts or resumes playback.
     */
    public void start() {
        mCurrentMediaPlayer.start();
    }

    /**
     * Resets the MediaPlayer to its uninitialized state.
     */
    public void stop() {
        mCurrentMediaPlayer.reset();
        mIsInitialized = false;
    }

    /**
     * Releases resources associated with this MediaPlayer object.
     */
    public void release() {
        stop();
        mCurrentMediaPlayer.release();

        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
    }

    public void reset() {
    }

    /**
     * Sets the volume on this player.
     *
     * @param leftVol Left and right volume scalar
     */
    public void setVolume(final float leftVol, final float rightVol) {
        mLeftVolume = leftVol;
        mRightVolume = rightVol;
        mCurrentMediaPlayer.setVolume(mLeftVolume, mRightVolume);
        if (mNextMediaPlayer != null) {
            mNextMediaPlayer.setVolume(mLeftVolume, mRightVolume);
        }
    }

    /**
     * Returns the audio session ID.
     *
     * @return The current audio session ID.
     */
    public int getAudioSessionId() {
        return mCurrentMediaPlayer.getAudioSessionId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCompletion(final MediaPlayer mp) {
        if (mLooping) {
            // do not control sequence if we are looping
            return;
        }
        if (mp == mCurrentMediaPlayer && mNextMediaPlayer != null) {
            mCurrentMediaPlayer.release();
            mCurrentMediaPlayer = mNextMediaPlayer;
            mNextMediaPlayer = null;
        }

        setNextDataSource(getNextUri());
    }

    private Uri getNextUri() {
        if (mRandom) {
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
        if (mCursor == null) {
            Log.e("MultiPlayer: mCursor = null");
            return null;
        }

        long id = mCursor.getLong(mColumnIndex);
        if (!mCursor.moveToNext()) {
            // Cycle through the playlist
            mCursor.moveToFirst();
        }
        if(mIsExternal) {
            return ContentUris.withAppendedId(Audio.Media.EXTERNAL_CONTENT_URI, id);
        } else {
            return ContentUris.withAppendedId(Audio.Media.INTERNAL_CONTENT_URI, id);
        }
    }

    private void handleSetDataSourceUri(Uri uri) {
        mSingle = false;
        if (uri.equals(RANDOM_URI)) {
            mRandom = true;
            return;
        }

        String columnName = null;
        final String rawUri = uri.toString();
        if (rawUri.startsWith(Audio.Media.EXTERNAL_CONTENT_URI.toString())
                || rawUri.startsWith(Audio.Media.INTERNAL_CONTENT_URI.toString())) {
            columnName = Audio.Media._ID;
        } else if (rawUri.startsWith(Audio.Playlists.EXTERNAL_CONTENT_URI.toString())
                || rawUri.startsWith(Audio.Playlists.INTERNAL_CONTENT_URI.toString())) {
            long playlist_id = Long.parseLong(uri.getLastPathSegment());
            uri = Audio.Playlists.Members.getContentUri("external", playlist_id);
            columnName = Audio.Playlists.Members.AUDIO_ID;
        } else {
            Log.e("MultiPlayer: unknown uri: "+uri.toString());
        }
        mIsExternal = rawUri.contains("external");
        mCursor = mContext.getContentResolver().query(uri, null, null, null, null);
        if (mCursor == null) {
            Log.e("MultiPlayr: Query failed, cursor = null");
            return;
        }
        mColumnIndex = mCursor.getColumnIndex(columnName);
        if (mCursor.getCount() == 0) {
            Log.e("MultiPlayr: Query failed, cursor.getCout() = 0");
            return;
        }
        mCursor.moveToFirst();

        if (mCursor.getCount() == 1) {
            mSingle = true;
        }
    }

    public boolean isPlaying() {
        return mCurrentMediaPlayer.isPlaying();
    }

    public void setOnErrorListener(MediaPlayer.OnErrorListener l) {
        mErrorListener = l;
    }

    public void setAudioStreamType(int type) {
        mAudioStreamType = type;
        mCurrentMediaPlayer.setAudioStreamType(mAudioStreamType);
        if(mNextMediaPlayer != null) {
            mNextMediaPlayer.setAudioStreamType(mAudioStreamType);
        }
    }

    public void setLooping(boolean looping) {
        if (mSingle) {
            mLooping = looping;
        } else {
            mLooping = false;
        }
        mCurrentMediaPlayer.setLooping(mLooping);
    }

    public void prepare() {
    }
}
