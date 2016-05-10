/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.android.deskclock.worldclock;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.deskclock.R;
import com.android.deskclock.Utils;
import com.android.deskclock.worldclock.AddCityDialog.CityTimeZone;

public class TimeZoneSpinner extends Spinner {
    private Context mContext;
    private CityTimeZone[] data;
    public AlertDialog mdialog;
    private ViewHolder holder;
    private LinearLayout ll_item;
    private LayoutParams linearParams;
    private SharedPreferences sp;
    public ListView listView;
    private int scrolledY;
    private int mposition;
    private static final int ITEM_COUNT_LAND = 5;
    private static final int ITEM_COUNT_PORT = 7;
    private static final String SELECTPOSITION = "select_position";
    ArrayAdapter<CityTimeZone> adapter;

    public TimeZoneSpinner(Context context) {
        super(context);
        mContext = context;
        sp = Utils.getCESharedPreferences(mContext);
    }

    public TimeZoneSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        if (sp == null) {
            sp = Utils.getCESharedPreferences(mContext);
        }
    }

    @Override
    public boolean performClick() {
        data = AddCityDialog.mZones;
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        final View view = inflater.inflate(R.layout.time_zone_list, null);
        // init listview
        initListView(view);
        creatDialog(view);
        return true;
    }

    private void initListView(View view) {
        listView = (ListView) view.findViewById(R.id.tz_list);
        // set ListView'height According to spinner'location
        int[] location = new int[2];
        this.getLocationOnScreen(location);
        final int spinnerLocationY = location[1];
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = spinnerLocationY;
        listView.setLayoutParams(params);
        adapter = new ArrayAdapter<CityTimeZone>(getContext(),
                R.layout.time_zone_list_item, data) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(mContext).inflate(
                            R.layout.time_zone_list_item, null);
                    holder = new ViewHolder();

                    convertView.setTag(holder);
                } else {
                    holder = (ViewHolder) convertView.getTag();
                }
                ll_item = (LinearLayout) convertView.findViewById(R.id.tz_ll);
                holder.tView = (TextView) convertView
                        .findViewById(android.R.id.text1);
                holder.tView.setText(data[position].toString());

                linearParams = (LayoutParams) ll_item.getLayoutParams();

                if (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    linearParams.height = spinnerLocationY / ITEM_COUNT_LAND;
                } else {
                    linearParams.height = spinnerLocationY / ITEM_COUNT_PORT;
                }

                ll_item.setLayoutParams(linearParams);
                return convertView;
            }
        };
        listView.setAdapter(adapter);
        listView.setDivider(null);
        listView.setSelection(sp.getInt(SELECTPOSITION, 0));
        listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                AddCityDialog.setSelectItem(position);
                mdialog.dismiss();
            }
        });
        listView.setOnScrollListener(new OnScrollListener() {

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                    mposition = listView.getFirstVisiblePosition();
                    sp.edit().putInt(SELECTPOSITION, mposition).commit();

                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                    int visibleItemCount, int totalItemCount) {

            }
        });
    }

    private class ViewHolder {
        TextView tView;
    }

    private void creatDialog(View view) {
        mdialog = new AlertDialog.Builder(mContext).create();
        Window mWindow = mdialog.getWindow();
        mWindow.setGravity(Gravity.TOP);
        WindowManager wm = (WindowManager) mContext
                .getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams lp = mWindow.getAttributes();
        mdialog.setCanceledOnTouchOutside(true);
        mdialog.show();
        mdialog.addContentView(view, lp);
    }
}
