/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.deskclock.uidata

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import androidx.annotation.IntegerRes
import androidx.annotation.StringRes

import com.android.deskclock.AlarmClockFragment
import com.android.deskclock.ClockFragment
import com.android.deskclock.R
import com.android.deskclock.Utils
import com.android.deskclock.stopwatch.StopwatchFragment
import com.android.deskclock.timer.TimerFragment

/**
 * All application-wide user interface data is accessible through this singleton.
 */
class UiDataModel private constructor() {
    /** Identifies each of the primary tabs within the application.  */
    enum class Tab(
        fragmentClass: Class<*>,
        @IntegerRes val pageResId: Int,
        @StringRes val labelResId: Int
    ) {
        ALARMS(AlarmClockFragment::class.java, R.id.page_alarm, R.string.menu_alarm),
        CLOCKS(ClockFragment::class.java, R.id.page_clock, R.string.menu_clock),
        TIMERS(TimerFragment::class.java, R.id.page_timer, R.string.menu_timer),
        STOPWATCH(StopwatchFragment::class.java,
                R.id.page_stopwatch, R.string.menu_stopwatch);

        val fragmentClassName: String = fragmentClass.name
    }

    private var mContext: Context? = null

    /** The model from which tab data are fetched.  */
    private lateinit var mTabModel: TabModel

    /** The model from which formatted strings are fetched.  */
    private lateinit var mFormattedStringModel: FormattedStringModel

    /** The model from which timed callbacks originate.  */
    private lateinit var mPeriodicCallbackModel: PeriodicCallbackModel

    /**
     * The context may be set precisely once during the application life.
     */
    fun init(context: Context, prefs: SharedPreferences) {
        if (mContext !== context) {
            mContext = context.applicationContext

            mPeriodicCallbackModel = PeriodicCallbackModel(mContext!!)
            mFormattedStringModel = FormattedStringModel(mContext!!)
            mTabModel = TabModel(prefs)
        }
    }

    /**
     * To display the alarm clock in this font, use the character [R.string.clock_emoji].
     *
     * @return a special font containing a glyph that draws an alarm clock
     */
    val alarmIconTypeface: Typeface
        get() = Typeface.createFromAsset(mContext!!.assets, "fonts/clock.ttf")

    //
    // Formatted Strings
    //

    /**
     * This method is intended to be used when formatting numbers occurs in a hotspot such as the
     * update loop of a timer or stopwatch. It returns cached results when possible in order to
     * provide speed and limit garbage to be collected by the virtual machine.
     *
     * @param value a positive integer to format as a String
     * @return the `value` formatted as a String in the current locale
     * @throws IllegalArgumentException if `value` is negative
     */
    fun getFormattedNumber(value: Int): String {
        Utils.enforceMainLooper()
        return mFormattedStringModel.getFormattedNumber(value)
    }

    /**
     * This method is intended to be used when formatting numbers occurs in a hotspot such as the
     * update loop of a timer or stopwatch. It returns cached results when possible in order to
     * provide speed and limit garbage to be collected by the virtual machine.
     *
     * @param value a positive integer to format as a String
     * @param length the length of the String; zeroes are padded to match this length
     * @return the `value` formatted as a String in the current locale and padded to the
     * requested `length`
     * @throws IllegalArgumentException if `value` is negative
     */
    fun getFormattedNumber(value: Int, length: Int): String {
        Utils.enforceMainLooper()
        return mFormattedStringModel.getFormattedNumber(value, length)
    }

    /**
     * This method is intended to be used when formatting numbers occurs in a hotspot such as the
     * update loop of a timer or stopwatch. It returns cached results when possible in order to
     * provide speed and limit garbage to be collected by the virtual machine.
     *
     * @param negative force a minus sign (-) onto the display, even if `value` is `0`
     * @param value a positive integer to format as a String
     * @param length the length of the String; zeroes are padded to match this length. If
     * `negative` is `true` the return value will contain a minus sign and a total
     * length of `length + 1`.
     * @return the `value` formatted as a String in the current locale and padded to the
     * requested `length`
     * @throws IllegalArgumentException if `value` is negative
     */
    fun getFormattedNumber(negative: Boolean, value: Int, length: Int): String {
        Utils.enforceMainLooper()
        return mFormattedStringModel.getFormattedNumber(negative, value, length)
    }

    /**
     * @param calendarDay any of the following values
     *
     *  * [Calendar.SUNDAY]
     *  * [Calendar.MONDAY]
     *  * [Calendar.TUESDAY]
     *  * [Calendar.WEDNESDAY]
     *  * [Calendar.THURSDAY]
     *  * [Calendar.FRIDAY]
     *  * [Calendar.SATURDAY]
     *
     * @return single-character version of weekday name; e.g.: 'S', 'M', 'T', 'W', 'T', 'F', 'S'
     */
    fun getShortWeekday(calendarDay: Int): String? {
        Utils.enforceMainLooper()
        return mFormattedStringModel.getShortWeekday(calendarDay)
    }

    /**
     * @param calendarDay any of the following values
     *
     *  * [Calendar.SUNDAY]
     *  * [Calendar.MONDAY]
     *  * [Calendar.TUESDAY]
     *  * [Calendar.WEDNESDAY]
     *  * [Calendar.THURSDAY]
     *  * [Calendar.FRIDAY]
     *  * [Calendar.SATURDAY]
     *
     * @return full weekday name; e.g.: 'Sunday', 'Monday', 'Tuesday', etc.
     */
    fun getLongWeekday(calendarDay: Int): String? {
        Utils.enforceMainLooper()
        return mFormattedStringModel.getLongWeekday(calendarDay)
    }

    //
    // Animations
    //

    /**
     * @return the duration in milliseconds of short animations
     */
    val shortAnimationDuration: Long
        get() {
            Utils.enforceMainLooper()
            return mContext!!.resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
        }

    /**
     * @return the duration in milliseconds of long animations
     */
    val longAnimationDuration: Long
        get() {
            Utils.enforceMainLooper()
            return mContext!!.resources.getInteger(android.R.integer.config_longAnimTime).toLong()
        }

    //
    // Tabs
    //

    /**
     * @param tabListener to be notified when the selected tab changes
     */
    fun addTabListener(tabListener: TabListener) {
        Utils.enforceMainLooper()
        mTabModel.addTabListener(tabListener)
    }

    /**
     * @param tabListener to no longer be notified when the selected tab changes
     */
    fun removeTabListener(tabListener: TabListener) {
        Utils.enforceMainLooper()
        mTabModel.removeTabListener(tabListener)
    }

    /**
     * @return the number of tabs
     */
    val tabCount: Int
        get() {
            Utils.enforceMainLooper()
            return mTabModel.tabCount
        }

    /**
     * @param ordinal the ordinal of the tab
     * @return the tab at the given `ordinal`
     */
    fun getTab(ordinal: Int): Tab {
        Utils.enforceMainLooper()
        return mTabModel.getTab(ordinal)
    }

    /**
     * @param position the position of the tab in the user interface
     * @return the tab at the given `ordinal`
     */
    fun getTabAt(position: Int): Tab {
        Utils.enforceMainLooper()
        return mTabModel.getTabAt(position)
    }

    var selectedTab: Tab
        /**
         * @return an enumerated value indicating the currently selected primary tab
         */
        get() {
            Utils.enforceMainLooper()
            return mTabModel.selectedTab
        }
        /**
         * @param tab an enumerated value indicating the newly selected primary tab
         */
        set(tab) {
            Utils.enforceMainLooper()
            mTabModel.setSelectedTab(tab)
        }

    /**
     * @param tabScrollListener to be notified when the scroll position of the selected tab changes
     */
    fun addTabScrollListener(tabScrollListener: TabScrollListener) {
        Utils.enforceMainLooper()
        mTabModel.addTabScrollListener(tabScrollListener)
    }

    /**
     * @param tabScrollListener to be notified when the scroll position of the selected tab changes
     */
    fun removeTabScrollListener(tabScrollListener: TabScrollListener) {
        Utils.enforceMainLooper()
        mTabModel.removeTabScrollListener(tabScrollListener)
    }

    /**
     * Updates the scrolling state in the [UiDataModel] for this tab.
     *
     * @param tab an enumerated value indicating the tab reporting its vertical scroll position
     * @param scrolledToTop `true` iff the vertical scroll position of the tab is at the top
     */
    fun setTabScrolledToTop(tab: Tab, scrolledToTop: Boolean) {
        Utils.enforceMainLooper()
        mTabModel.setTabScrolledToTop(tab, scrolledToTop)
    }

    /**
     * @return `true` iff the content in the selected tab is currently scrolled to the top
     */
    val isSelectedTabScrolledToTop: Boolean
        get() {
            Utils.enforceMainLooper()
            return mTabModel.isTabScrolledToTop(selectedTab)
        }

    //
    // Shortcut Ids
    //

    /**
     * @param category which category of shortcut of which to get the id
     * @param action the desired action to perform
     * @return the id of the shortcut
     */
    fun getShortcutId(@StringRes category: Int, @StringRes action: Int): String {
        return if (category == R.string.category_stopwatch) {
            mContext!!.getString(category)
        } else {
            mContext!!.getString(category) + "_" + mContext!!.getString(action)
        }
    }

    //
    // Timed Callbacks
    //

    /**
     * @param runnable to be called every minute
     * @param offset an offset applied to the minute to control when the callback occurs
     */
    fun addMinuteCallback(runnable: Runnable, offset: Long) {
        Utils.enforceMainLooper()
        mPeriodicCallbackModel.addMinuteCallback(runnable, offset)
    }

    /**
     * @param runnable to be called every quarter-hour
     */
    fun addQuarterHourCallback(runnable: Runnable) {
        Utils.enforceMainLooper()
        mPeriodicCallbackModel.addQuarterHourCallback(runnable)
    }

    /**
     * @param runnable to be called every hour
     */
    fun addHourCallback(runnable: Runnable) {
        Utils.enforceMainLooper()
        mPeriodicCallbackModel.addHourCallback(runnable)
    }

    /**
     * @param runnable to be called every midnight
     */
    fun addMidnightCallback(runnable: Runnable) {
        Utils.enforceMainLooper()
        mPeriodicCallbackModel.addMidnightCallback(runnable)
    }

    /**
     * @param runnable to no longer be called periodically
     */
    fun removePeriodicCallback(runnable: Runnable) {
        Utils.enforceMainLooper()
        mPeriodicCallbackModel.removePeriodicCallback(runnable)
    }

    companion object {
        /** The single instance of this data model that exists for the life of the application.  */
        val sUiDataModel = UiDataModel()

        @get:JvmStatic
        val uiDataModel
            get() = sUiDataModel
    }
}