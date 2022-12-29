/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.android.deskclock.actionbarmenu.MenuItemControllerFactory;
import com.android.deskclock.actionbarmenu.NightModeMenuItemController;
import com.android.deskclock.actionbarmenu.OptionsMenuManager;
import com.android.deskclock.actionbarmenu.SettingsMenuItemController;
import com.android.deskclock.data.DataModel;
import com.android.deskclock.data.DataModel.SilentSetting;
import com.android.deskclock.data.OnSilentSettingsListener;
import com.android.deskclock.events.Events;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.uidata.TabListener;
import com.android.deskclock.uidata.UiDataModel;
import com.android.deskclock.widget.toast.SnackbarManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.snackbar.Snackbar;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
import static android.text.format.DateUtils.SECOND_IN_MILLIS;
import static com.android.deskclock.AnimatorUtils.getScaleAnimator;

import java.util.ArrayList;
import java.util.List;

/**
 * The main activity of the application which displays 4 different tabs contains alarms, world
 * clocks, timers and a stopwatch.
 */
public class DeskClock extends BaseActivity
        implements FabContainer, LabelDialogFragment.AlarmLabelDialogHandler {

    /** Coordinates handling of context menu items. */
    private final OptionsMenuManager mOptionsMenuManager = new OptionsMenuManager();

    /** Shrinks the {@link #mFab}, {@link #mLeftButton} and {@link #mRightButton} to nothing. */
    private final AnimatorSet mHideAnimation = new AnimatorSet();

    /** Grows the {@link #mFab}, {@link #mLeftButton} and {@link #mRightButton} to natural sizes. */
    private final AnimatorSet mShowAnimation = new AnimatorSet();

    /** Hides, updates, and shows only the {@link #mFab}; the buttons are untouched. */
    private final AnimatorSet mUpdateFabOnlyAnimation = new AnimatorSet();

    /** Hides, updates, and shows only the {@link #mLeftButton} and {@link #mRightButton}. */
    private final AnimatorSet mUpdateButtonsOnlyAnimation = new AnimatorSet();

    /** Updates the user interface to reflect the selected tab from the backing model. */
    private final TabListener mTabChangeWatcher = new TabChangeWatcher();

    /** Shows/hides a snackbar explaining which setting is suppressing alarms from firing. */
    private final OnSilentSettingsListener mSilentSettingChangeWatcher =
            new SilentSettingChangeWatcher();

    /** Displays a snackbar explaining why alarms may not fire or may fire silently. */
    private Runnable mShowSilentSettingSnackbarRunnable;

    /** The view to which snackbar items are anchored. */
    private View mSnackbarAnchor;

    /** The single floating-action button shared across all tabs in the user interface. */
    private ImageView mFab;

    /** The button left of the {@link #mFab} shared across all tabs in the user interface. */
    private ImageView mLeftButton;

    /** The button right of the {@link #mFab} shared across all tabs in the user interface. */
    private ImageView mRightButton;

    /** The view that displays the current tab's title */
    private TextView mTitleView;

    /** The bottom navigation bar */
    private BottomNavigationView mBottomNavigation;

    private FragmentUtils mFragmentUtils;

    /** {@code true} when a settings change necessitates recreating this activity. */
    private boolean mRecreateActivity;

    private static final String PERMISSION_POWER_OFF_ALARM =
            "org.codeaurora.permission.POWER_OFF_ALARM";

    private static final int CODE_FOR_ALARM_PERMISSION = 1;

    private static final int INVALID_RES = -1;

    private static final int[] PERMISSION_ERROR_MESSAGE_RES_IDS = {
            0,
            R.string.dialog_permissions_post_notifications,
    };

    @Override
    public void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);

        // Fragments may query the latest intent for information, so update the intent.
        setIntent(newIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.desk_clock);
        mSnackbarAnchor = findViewById(R.id.content);

        checkPermissions();

        // Configure the toolbar.
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }

        // Configure the menu item controllers add behavior to the toolbar.
        mOptionsMenuManager.addMenuItemController(
                new NightModeMenuItemController(this), new SettingsMenuItemController(this));
        mOptionsMenuManager.addMenuItemController(
                MenuItemControllerFactory.getInstance().buildMenuItemControllers(this));

        // Inflate the menu during creation to avoid a double layout pass. Otherwise, the menu
        // inflation occurs *after* the initial draw and a second layout pass adds in the menu.
        onCreateOptionsMenu(toolbar.getMenu());

        // Configure the buttons shared by the tabs.
        mFab = findViewById(R.id.fab);
        mLeftButton = findViewById(R.id.left_button);
        mRightButton = findViewById(R.id.right_button);

        mFab.setOnClickListener(view -> getSelectedDeskClockFragment().onFabClick(mFab));
        mLeftButton.setOnClickListener(view ->
                getSelectedDeskClockFragment().onLeftButtonClick(mLeftButton));
        mRightButton.setOnClickListener(view ->
                getSelectedDeskClockFragment().onRightButtonClick(mRightButton));

        final long duration = UiDataModel.getUiDataModel().getShortAnimationDuration();

        final ValueAnimator hideFabAnimation = getScaleAnimator(mFab, 1f, 0f);
        final ValueAnimator showFabAnimation = getScaleAnimator(mFab, 0f, 1f);

        final ValueAnimator leftHideAnimation = getScaleAnimator(mLeftButton, 1f, 0f);
        final ValueAnimator rightHideAnimation = getScaleAnimator(mRightButton, 1f, 0f);
        final ValueAnimator leftShowAnimation = getScaleAnimator(mLeftButton, 0f, 1f);
        final ValueAnimator rightShowAnimation = getScaleAnimator(mRightButton, 0f, 1f);

        hideFabAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                getSelectedDeskClockFragment().onUpdateFab(mFab);
            }
        });

        leftHideAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                getSelectedDeskClockFragment().onUpdateFabButtons(mLeftButton, mRightButton);
            }
        });

        // Build the reusable animations that hide and show the fab and left/right buttons.
        // These may be used independently or be chained together.
        mHideAnimation
                .setDuration(duration)
                .play(hideFabAnimation)
                .with(leftHideAnimation)
                .with(rightHideAnimation);

        mShowAnimation
                .setDuration(duration)
                .play(showFabAnimation)
                .with(leftShowAnimation)
                .with(rightShowAnimation);

        // Build the reusable animation that hides and shows only the fab.
        mUpdateFabOnlyAnimation
                .setDuration(duration)
                .play(showFabAnimation)
                .after(hideFabAnimation);

        // Build the reusable animation that hides and shows only the buttons.
        mUpdateButtonsOnlyAnimation
                .setDuration(duration)
                .play(leftShowAnimation)
                .with(rightShowAnimation)
                .after(leftHideAnimation)
                .after(rightHideAnimation);

        mFragmentUtils = new FragmentUtils(this);
        // Mirror changes made to the selected tab into UiDataModel.
        mBottomNavigation = findViewById(R.id.bottom_view);
        mBottomNavigation.setOnItemSelectedListener(mNavigationListener);

        // Honor changes to the selected tab from outside entities.
        UiDataModel.getUiDataModel().addTabListener(mTabChangeWatcher);

        mTitleView = findViewById(R.id.title_view);
    }

    private final NavigationBarView.OnItemSelectedListener mNavigationListener
            = new BottomNavigationView.OnItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            UiDataModel.Tab selectedTab = null;
            int itemId = item.getItemId();
            if (itemId == R.id.page_alarm) {
                selectedTab = UiDataModel.Tab.ALARMS;
            } else if (itemId == R.id.page_clock) {
                selectedTab = UiDataModel.Tab.CLOCKS;
            } else if (itemId == R.id.page_timer) {
                selectedTab = UiDataModel.Tab.TIMERS;
            } else if (itemId == R.id.page_stopwatch) {
                selectedTab = UiDataModel.Tab.STOPWATCH;
            }

            if (selectedTab != null) {
                UiDataModel.Tab currentTab = UiDataModel.getUiDataModel().getSelectedTab();
                DeskClockFragment currentFrag = mFragmentUtils.getDeskClockFragment(currentTab);
                DeskClockFragment selectedFrag = mFragmentUtils.getDeskClockFragment(selectedTab);

                int currentVisibility = currentFrag.getFabTargetVisibility();
                int targetVisibility = selectedFrag.getFabTargetVisibility();
                if (currentVisibility != targetVisibility) {
                    if (targetVisibility == View.VISIBLE) {
                        mShowAnimation.start();
                    } else {
                        mHideAnimation.start();
                    }
                }
                UiDataModel.getUiDataModel().setSelectedTab(selectedTab);
                return true;
            }

            return false;
        }
    };

    @Override
    protected void onStart() {
        DataModel.getDataModel().addSilentSettingsListener(mSilentSettingChangeWatcher);
        DataModel.getDataModel().setApplicationInForeground(true);
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ViewPager does not save state; this honors the selected tab in the user interface.
        updateCurrentTab();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        if (mRecreateActivity) {
            mRecreateActivity = false;
        }
    }

    @Override
    protected void onStop() {
        DataModel.getDataModel().removeSilentSettingsListener(mSilentSettingChangeWatcher);
        if (!isChangingConfigurations()) {
            DataModel.getDataModel().setApplicationInForeground(false);
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        UiDataModel.getUiDataModel().removeTabListener(mTabChangeWatcher);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenuManager.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mOptionsMenuManager.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mOptionsMenuManager.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    /**
     * Called by the LabelDialogFormat class after the dialog is finished.
     */
    @Override
    public void onDialogLabelSet(Alarm alarm, String label, String tag) {
        final Fragment frag = getSupportFragmentManager().findFragmentByTag(tag);
        if (frag instanceof AlarmClockFragment) {
            ((AlarmClockFragment) frag).setLabel(alarm, label);
        }
    }

    /**
     * Listens for keyboard activity for the tab fragments to handle if necessary. A tab may want to
     * respond to key presses even if they are not currently focused.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return getSelectedDeskClockFragment().onKeyDown(keyCode,event)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public void updateFab(@UpdateFabFlag int updateType) {
        final DeskClockFragment f = getSelectedDeskClockFragment();
        final int fabAnimationType = updateType & FAB_ANIMATION_MASK;
        if (fabAnimationType == FAB_SHRINK_AND_EXPAND) {
            mUpdateFabOnlyAnimation.start();
        } else if (fabAnimationType == FAB_IMMEDIATE) {
            f.onUpdateFab(mFab);
        } else if (fabAnimationType == FAB_MORPH) {
            f.onMorphFab(mFab);
        }
        final int fabRequestFocus = updateType & FAB_REQUEST_FOCUS_MASK;
        if (fabRequestFocus == FAB_REQUEST_FOCUS) {
            mFab.requestFocus();
        }
        final int buttonsAnimationType = updateType & BUTTONS_ANIMATION_MASK;
        if (buttonsAnimationType == BUTTONS_IMMEDIATE) {
            f.onUpdateFabButtons(mLeftButton, mRightButton);
        } else if (buttonsAnimationType == BUTTONS_SHRINK_AND_EXPAND) {
            mUpdateButtonsOnlyAnimation.start();
        }
        final int buttonsDisable = updateType & BUTTONS_DISABLE_MASK;
        if (buttonsDisable == BUTTONS_DISABLE) {
            mLeftButton.setClickable(false);
            mRightButton.setClickable(false);
        }
        final int fabAndButtonsShrinkExpandType = updateType & FAB_AND_BUTTONS_SHRINK_EXPAND_MASK;
        if (fabAndButtonsShrinkExpandType == FAB_AND_BUTTONS_SHRINK) {
            mHideAnimation.start();
        } else if (fabAndButtonsShrinkExpandType == FAB_AND_BUTTONS_EXPAND) {
            mShowAnimation.start();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Recreate the activity if any settings have been changed
        if (requestCode == SettingsMenuItemController.REQUEST_CHANGE_SETTINGS
                && resultCode == RESULT_OK) {
            mRecreateActivity = true;
        }
    }

    private void checkPermissions() {
        final List<String> missingPermissions = new ArrayList<>();
        if (!hasPowerOffPermission()) {
            missingPermissions.add(PERMISSION_POWER_OFF_ALARM);
        }
        if (!hasNotificationPermission()) {
            if (Build.VERSION.SDK_INT >= 33) {
                missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!missingPermissions.isEmpty()) {
            final String[] requestArray = missingPermissions.toArray(new String[0]);
            requestPermissions(requestArray, CODE_FOR_ALARM_PERMISSION);
        }
    }

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasPowerOffPermission() {
        return hasPermission(PERMISSION_POWER_OFF_ALARM);
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return hasPermission(Manifest.permission.POST_NOTIFICATIONS);
        }
        return true;
    }

    private boolean hasEssentialPermissions() {
        return hasNotificationPermission();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CODE_FOR_ALARM_PERMISSION) {
            if (hasEssentialPermissions()) {
                LogUtils.i("Essential permissions granted!");
                if (hasPermission(PERMISSION_POWER_OFF_ALARM)) {
                    LogUtils.i("Power off alarm permission is granted.");
                } else {
                    showRationale(PERMISSION_POWER_OFF_ALARM,
                            R.string.dialog_permissions_power_off_alarm, INVALID_RES, false);
                }
            } else {
                essentialPermissionsDenied();
            }
        }
    }

    private void showRationale(String permission, @StringRes int messageRes,
                               @StringRes int errorRes, boolean finishWhenDenied) {
        if (shouldShowRequestPermissionRationale(permission)) {
            showPermissionRationale(messageRes, this::checkPermissions, finishWhenDenied);
        } else if (errorRes != INVALID_RES){
            showPermissionError(errorRes, finishWhenDenied);
        }
    }

    private void essentialPermissionsDenied() {
        if ((!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) &&
                !hasNotificationPermission())) {
            showPermissionError(R.string.dialog_permissions_no_permission, true);
        } else {
            // Explain the user why the denied permission is needed
            int error = 0;

            if (!hasNotificationPermission()) {
                error |= 1;
            }

            showPermissionRationale(PERMISSION_ERROR_MESSAGE_RES_IDS[error],
                    this::checkPermissions, true);
        }
    }

    private void showPermissionRationale(@StringRes int messageRes, Runnable requestAgain,
                                         Boolean finishWhenDenied) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_permissions_title)
                .setMessage(messageRes)
                .setPositiveButton(R.string.dialog_permissions_ask,
                        (dialog, position) -> {
                            dialog.dismiss();
                            requestAgain.run();
                        })
                .setNegativeButton(R.string.dialog_permissions_dismiss, (dialog, position) ->
                        maybeFinish(finishWhenDenied))
                .show();
    }

    private void showPermissionError(@StringRes int messageRes, boolean finishWhenDenied) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_permissions_title)
                .setMessage(messageRes)
                .setPositiveButton(R.string.dialog_permissions_settings, (dialog, position) ->
                        startActivity(new Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", getPackageName(), null))
                                .addFlags(FLAG_ACTIVITY_NEW_TASK)))
                .setNegativeButton(R.string.dialog_permissions_dismiss, (dialog, position) ->
                        maybeFinish(finishWhenDenied))
                .setOnDismissListener(dialog -> maybeFinish(finishWhenDenied))
                .show();
    }

    private void maybeFinish(boolean finish) {
        if (finish) {
            finish();
        }
    }

    /**
     * Configure the {@link #mBottomNavigation} to display UiDataModel's selected tab.
     */
    private void updateCurrentTab() {
        // Fetch the selected tab from the source of truth: UiDataModel.
        final UiDataModel.Tab selectedTab = UiDataModel.getUiDataModel().getSelectedTab();
        // Update the selected tab in the mBottomNavigation if it does not agree with UiDataModel.
        mBottomNavigation.setSelectedItemId(selectedTab.getPageResId());
        mFragmentUtils.showFragment(selectedTab);
        mTitleView.setText(selectedTab.getLabelResId());
    }

    /**
     * @return the DeskClockFragment that is currently selected according to UiDataModel
     */
    private DeskClockFragment getSelectedDeskClockFragment() {
        return mFragmentUtils.getCurrentFragment();
    }

    /**
     * @return a Snackbar that displays the message with the given id for 5 seconds
     */
    private Snackbar createSnackbar(@StringRes int messageId) {
        return Snackbar.make(mSnackbarAnchor, messageId, 5000 /* duration */);
    }

    /**
     * Shows/hides a snackbar as silencing settings are enabled/disabled.
     */
    private final class SilentSettingChangeWatcher implements OnSilentSettingsListener {
        @Override
        public void onSilentSettingsChange(SilentSetting after) {
            if (mShowSilentSettingSnackbarRunnable != null) {
                mSnackbarAnchor.removeCallbacks(mShowSilentSettingSnackbarRunnable);
                mShowSilentSettingSnackbarRunnable = null;
            }

            if (after == null) {
                SnackbarManager.dismiss();
            } else {
                mShowSilentSettingSnackbarRunnable = new ShowSilentSettingSnackbarRunnable(after);
                mSnackbarAnchor.postDelayed(mShowSilentSettingSnackbarRunnable, SECOND_IN_MILLIS);
            }
        }
    }

    /**
     * Displays a snackbar that indicates a system setting is currently silencing alarms.
     */
    private final class ShowSilentSettingSnackbarRunnable implements Runnable {

        private final SilentSetting mSilentSetting;

        private ShowSilentSettingSnackbarRunnable(SilentSetting silentSetting) {
            mSilentSetting = silentSetting;
        }

        public void run() {
            // Create a snackbar with a message explaining the setting that is silencing alarms.
            final Snackbar snackbar = createSnackbar(mSilentSetting.getLabelResId());

            // Set the associated corrective action if one exists.
            if (mSilentSetting.isActionEnabled(DeskClock.this)) {
                final int actionResId = mSilentSetting.getActionResId();
                snackbar.setAction(actionResId, mSilentSetting.getActionListener());
            }

            SnackbarManager.show(snackbar);
        }
    }

    /**
     * As the model reports changes to the selected tab, update the user interface.
     */
    private final class TabChangeWatcher implements TabListener {
        @Override
        public void selectedTabChanged(UiDataModel.Tab newSelectedTab) {
            // Update the view pager and tab layout to agree with the model.
            updateCurrentTab();

            // Avoid sending events for the initial tab selection on launch and re-selecting a tab
            // after a configuration change.
            if (DataModel.getDataModel().isApplicationInForeground()) {
                switch (newSelectedTab) {
                    case ALARMS:
                        Events.sendAlarmEvent(R.string.action_show, R.string.label_deskclock);
                        break;
                    case CLOCKS:
                        Events.sendClockEvent(R.string.action_show, R.string.label_deskclock);
                        break;
                    case TIMERS:
                        Events.sendTimerEvent(R.string.action_show, R.string.label_deskclock);
                        break;
                    case STOPWATCH:
                        Events.sendStopwatchEvent(R.string.action_show, R.string.label_deskclock);
                        break;
                }
            }

            // If the hide animation has already completed, the buttons must be updated now when the
            // new tab is known. Otherwise they are updated at the end of the hide animation.
            if (!mHideAnimation.isStarted()) {
                getSupportFragmentManager().executePendingTransactions();
                updateFab(FAB_AND_BUTTONS_IMMEDIATE);
            }
        }
    }
}