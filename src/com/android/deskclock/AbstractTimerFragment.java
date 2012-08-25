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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.android.deskclock.DigitalTimerClock.OnEditListener;

/**
 * The base class for all the timer fragments
 */
public abstract class AbstractTimerFragment extends Fragment implements
    DeskClock.Fragmentable, DeskClock.OnBroadcastReceiver, DeskClock.FocusableFragment,
    DeskClock.ReportableFragment, View.OnLongClickListener, OnEditListener {

    private static final boolean DEBUG = true;

    private boolean mBroadcastReceiverReady = false;

    private static final String PREF_TIMER_ID = "timer_id"; //$NON-NLS-1$

    // The timer identifier. This is use to identify the timer in the service.
    // A new identifier is returned when a timer is created
    private long mTimerId;
    private ITimerClockService mTimerClockService;
    private boolean mTimerClockServiceBound;

    private Activity mActivity;
    private Handler mHandler;

    // The timer service connection listener interface
    private final ServiceConnection mTimerClockServiceConnection = new ServiceConnection() {
        @SuppressWarnings("synthetic-access")
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) Log.v(getLogTag(), "onServiceDisconnected"); //$NON-NLS-1$
            AbstractTimerFragment.this.mTimerClockServiceBound = false;

            // Update data?
            if (isTimerCreated()) {
                AbstractTimerFragment.this.mHandler.post(new Runnable() {
                    public void run() {
                        // Advice the user and reset timer
                        showExceptionDialog(null, null, R.string.msg_timer_service_is_not_ready);
                        resetTimer();
                    }
                });
            }

            // Broadcast
            AbstractTimerFragment.this.onServiceDisconnected();
        }

        @SuppressWarnings("synthetic-access")
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG) Log.v(getLogTag(), "onServiceConnected"); //$NON-NLS-1$
            AbstractTimerFragment.this.mTimerClockService = (ITimerClockService)service;
            AbstractTimerFragment.this.mTimerClockServiceBound = true;

            // Update data?
            AbstractTimerFragment.this.mHandler.post(new Runnable() {
                public void run() {
                    // Timer was created?
                    if (isTimerCreated()) {
                        try {
                            //Try to the refresh the data
                            updateTimers();
                            if (isTimerRunning()) {
                                // Attach the drawing handler
                                AbstractTimerFragment.this.mRedrawUiThread.reset();
                                AbstractTimerFragment.this.mHandler.post(
                                        AbstractTimerFragment.this.mRedrawUiThread);
                            }

                            // The UI need to be checked
                            onUiChanged();

                        } catch (RemoteException rEx) {
                            resetTimer();
                        }
                    }

                    // Broadcast
                    AbstractTimerFragment.this.onServiceConnected();
                }
            });
        }
    };

    // A thread to redraw the UI, implemented as a recursive handler
    private class RedrawUiThread implements Runnable {
        private boolean mCancel;

        /**
         * Method that reset the drawing thread
         */
        public void reset() {
            this.mCancel = false;
        }

        /**
         * Method that cancels the execution of the drawing thread
         */
        public void cancel() {
            this.mCancel = true;
        }

        @SuppressWarnings("synthetic-access")
        public void run() {
            try {
                if (!this.mCancel) {
                    // Update the timers
                    updateTimers();
                    if (!continueRedrawing()) {
                        this.mCancel = true;
                    } else {
                        AbstractTimerFragment.this.mHandler.post(this);
                    }
                    onUiChanged();
                } else {
                    Log.i(getLogTag(), "RedrawUiThread paused"); //$NON-NLS-1$
                }

            } catch (RemoteException rEx) {
                Log.e(getLogTag(), "RedrawUiThread exception", rEx); //$NON-NLS-1$

            } catch (Exception ex) {
                Log.e(getLogTag(), "RedrawUiThread exception", ex); //$NON-NLS-1$
            }
        }
    }
    @SuppressWarnings("synthetic-access")
    private final RedrawUiThread mRedrawUiThread = new RedrawUiThread();

    /**
     * {@inheritDoc}
     */
    public boolean isBroadcastReceiverReady() {
        return this.mBroadcastReceiverReady;
    }

    /**
     * {@inheritDoc}
     */
    public void onBroadcastReceiver(Context context, Intent intent) {/**NON BLOCK**/}

    /**
     * {@inheritDoc}
     */
    public void onGainFocus() {/**NON BLOCK**/}

    /**
     * {@inheritDoc}
     */
    public void onLostFocus() {/**NON BLOCK**/}

    /**
     * {@inheritDoc}
     */
    public void onNotifyUserInteraction() {/**NON BLOCK**/}

    /**
     * {@inheritDoc}
     */
    public boolean onNotifyIntentChanged(Intent intent) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean onNotifyPrepareOptionsMenu(Menu menu) {
        if (this.mActivity != null) {
            // Processed
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean onNotifyOptionsItemSelected(MenuItem item) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void OnEnterEdit() {/**NON BLOCK**/}

    /**
     * {@inheritDoc}
     */
    public void OnExitEdit() {/**NON BLOCK**/}

    /**
     * {@inheritDoc}
     */
    public boolean onNotifyBackPressed() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity) {
        if (DEBUG) Log.v(getLogTag(), "onAttach: " + activity); //$NON-NLS-1$
        super.onAttach(activity);

        this.mActivity = activity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.v(getLogTag(), "onCreate"); //$NON-NLS-1$
        super.onCreate(savedInstanceState);
        this.mBroadcastReceiverReady = true;
        this.mHandler = new Handler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStart() {
        if (DEBUG) Log.v(getLogTag(), "onStart"); //$NON-NLS-1$
        super.onStart();

        // Start the service
        Intent intent1 = new Intent(this.mActivity, TimerClockService.class);
        this.mActivity.startService(intent1);

        // Bind to the service
        Intent intent2 = new Intent(this.mActivity, TimerClockService.class);
        this.mActivity.bindService(
                intent2, this.mTimerClockServiceConnection,
                Context.BIND_ABOVE_CLIENT | Context.BIND_AUTO_CREATE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop() {
        if (DEBUG) Log.v(getLogTag(), "onStop"); //$NON-NLS-1$
        super.onStop();

        // Unbind the service
        this.mActivity.unbindService(this.mTimerClockServiceConnection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        if (DEBUG) Log.v(getLogTag(), "onPause"); //$NON-NLS-1$
        super.onPause();

        // Stop a the UI thread
        this.mRedrawUiThread.cancel();

        // Save all state data to preferences file
        saveData();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        if (DEBUG) Log.v(getLogTag(), "onResume"); //$NON-NLS-1$
        super.onResume();

        // Restore the instance from preferences file
        readData();

        // Request a UI changed broadcast
        onUiChanged();
    }

    /**
     * {@inheritDoc}
     */
    public boolean onLongClick(View v) {
        Toast.makeText(this.mActivity, v.getContentDescription(), Toast.LENGTH_SHORT).show();
        return true;
    }

    /**
     * Method that returns if there are a valid timer reference
     *
     * @return boolean If there are a valid timer reference
     */
    protected boolean isTimerCreated() {
        return this.mTimerId != 0;
    }

    /**
     * Method that returns if the timer has a valid reference
     *
     * @return boolean If the timer has a valid reference
     */
    protected boolean isTimerValid() {
        try {
            if (!isTimerCreated()
                || !this.mTimerClockServiceBound
                || this.mTimerClockService == null) {
                return false;
            }
            this.mTimerClockService.isRunning(this.mTimerId);
            return true;
        } catch (RemoteException rEx) {
            return false;
        }
    }

    /**
     * Method that returns if the timer is running
     *
     * @return boolean If the timer is running
     */
    protected boolean isTimerRunning() {
        // If has a valid reference and service returns that service is running
        try {
            return isTimerCreated() &&
                    (this.mTimerClockServiceBound &&
                     this.mTimerClockService.isRunning(this.mTimerId));
        } catch (RemoteException rEx) {
            this.mTimerId = 0;
            return false;
        }
    }

    /**
     * Method that shows a warning dialog
     *
     * @param logMsg The message to log
     * @param msg The message to display
     * @param ex The exception
     */
    protected void showExceptionDialog(String logMsg, Exception ex, int msg) {
        if (logMsg != null && ex != null) {
            Log.e(getLogTag(), logMsg, ex);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this.mActivity);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(android.R.string.dialog_alert_title);
        builder.setMessage(msg);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.create().show();
    }

    /**
     * Method that returns the activity of fragment
     *
     * @return Activity The activity of fragment
     */
    protected Activity getFragmentActivity() {
        return this.mActivity;
    }

    /**
     * Method that returns the timer identifier
     *
     * @return long The timer identifier
     */
    public long getTimerId() {
        return this.mTimerId;
    }

    /**
     * Method that returns the timer service
     *
     * @return ITimerClockService The timer service
     */
    public ITimerClockService getTimerClockService() {
        return this.mTimerClockService;
    }

    /**
     * Method that starts the timer
     *
     * @param type The kind of timer
     */
    protected void startTimer(TimerClockService.TIMER_TYPE type) {
     // Ensure that timer service was started
        if (!this.mTimerClockServiceBound) {
            showExceptionDialog(null, null, R.string.msg_timer_service_is_not_ready);
        }
        if (isTimerRunning()) {
            return;
        }

        try {
            // Is a new timer necessary?
            if (!isTimerValid()) {
                // Create a new timer
                this.mTimerId =
                        this.mTimerClockService.createTimer(
                                type.ordinal());

                // Broadcast a timer creation
                onCreateTimer();
            }

            // Start the timer
            this.mTimerClockService.startTimer(this.mTimerId);

            // Broadcast a timer start
            onStartTimer();

            // Start a recursive drawing handler
            this.mRedrawUiThread.reset();
            this.mHandler.post(this.mRedrawUiThread);

        } catch (RemoteException rEx) {
            // If timer was created, destroy
            if (this.mTimerId != 0) {
                try {
                    this.mTimerClockService.destroyTimer(this.mTimerId);

                    // Broadcast a timer destroy
                    onDestroyTimer();
                } catch (Exception e) {/**NON BLOCK**/}
            }

            // Show the exception
            showExceptionDialog(
                    "TimerClockService exception", //$NON-NLS-1$
                    rEx, R.string.msg_timer_creation_fails);

        } catch (Exception ex) {
            // If timer was created, destroy
            if (this.mTimerId != 0) {
                try {
                    this.mTimerClockService.destroyTimer(this.mTimerId);

                    // Broadcast a timer destroy
                    onDestroyTimer();
                } catch (Exception e) {/**NON BLOCK**/}
            }

            // Show the exception
            showExceptionDialog(
                    "UIThread exception", ex, R.string.msg_timer_ui_thread_fails); //$NON-NLS-1$
        }
    }

    /**
     * Method that stops the timer
     *
     * @param reset Indicates if the caller origin is a reset request
     */
    protected void stopTimer(boolean reset) {
        // Ensure that timer service was started
        if (!this.mTimerClockServiceBound) {
            showExceptionDialog(null, null, R.string.msg_timer_service_is_not_ready);
            return;
        }
        if (!isTimerRunning()) {
            return;
        }

        try {
            // Stop the timer and take partial
            this.mTimerClockService.stopTimer(this.mTimerId);

            // Broadcast a timer stop
            onStopTimer(reset);

            // Stop a the UI thread
            this.mRedrawUiThread.cancel();

        } catch (RemoteException rEx) {
            showExceptionDialog(
                    "TimerClockService exception",  //$NON-NLS-1$
                    rEx, R.string.msg_timer_creation_fails);

        } catch (Exception ex) {
            showExceptionDialog(
                    "UIThread exception",  //$NON-NLS-1$
                    ex, R.string.msg_timer_ui_thread_fails);
        }
    }

    /**
     * Method that reset the timer
     */
    protected void resetTimer() {
        // Ensure that timer service was started. Then destroy the timer
        stopTimer(true);
        try {
            this.mTimerClockService.destroyTimer(this.mTimerId);
        } catch (Exception e) {/**NON BLOCK**/}
        this.mTimerId = 0;

        // Remove notification?
        try {
            this.mTimerClockService.removeNotification(AbstractTimerFragment.this.mTimerId);
        } catch (Exception e) {/**NON BLOCK**/}
    }

    /**
     * Method that queries the timer service for the elapsed time
     *
     * @return long The elapsed time
     * @throws RemoteException If an error occurs accesing the service
     */
    protected long queryTimer() throws RemoteException {
        return this.mTimerClockService.queryTime(this.mTimerId);
    }

    /**
     * Method that runs in the UI thread
     *
     * @param runnable What to run in the UI thread
     */
    protected void runInUiHandler(Runnable runnable) {
        this.mHandler.post(runnable);
    }

    /**
     * Method that save the data to disk
     */
    protected void saveData() {
        // Save the timer state to preference
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        editor.putLong(PREF_TIMER_ID, this.mTimerId);
        editor.commit();
    }

    /**
     * Method that read the data from disk
     */
    protected void readData() {
        // Read the timer state to preference
        SharedPreferences sp = getSharedPreferences();
        this.mTimerId = sp.getLong(PREF_TIMER_ID, 0);
    }

    /**
     * Method invoked to query if the drawing thread must be stopped
     */
    @SuppressWarnings("static-method")
    protected boolean continueRedrawing() {
        return true;
    }

    /**
     * Method invoked when a timer is created
     */
    protected void onCreateTimer() {/**NON BLOCK**/}

    /**
     * Method invoked when a timer is started
     */
    protected void onStartTimer() {/**NON BLOCK**/}

    /**
     * Method invoked when a timer is stopped
     *
     * @param reset Indicates if the caller origin is a reset request
     */
    protected void onStopTimer(boolean reset) {/**NON BLOCK**/}

    /**
     * Method invoked when a timer is destroyed
     */
    protected void onDestroyTimer() {/**NON BLOCK**/}

    /**
     * Method invoked when a serviced is connected
     */
    protected void onServiceConnected() {/**NON BLOCK**/}

    /**
     * Method invoked when a serviced is disconnected
     */
    protected void onServiceDisconnected() {/**NON BLOCK**/}

    /**
     * Method that returns the {@link SharedPreferences} where to save the data
     *
     * @return SharedPreferences Where to save the data
     */
    protected abstract SharedPreferences getSharedPreferences();

    /**
     * Method that update the timers of the fragments
     *
     * @throws RemoteException If there some error accessing the timer service
     */
    protected abstract void updateTimers() throws RemoteException;

    /**
     * Method that returns the tag to show in logs
     */
    protected abstract String getLogTag();

    /**
     * Method that invoked when the UI need to be updated
     */
    protected abstract void onUiChanged();

}

