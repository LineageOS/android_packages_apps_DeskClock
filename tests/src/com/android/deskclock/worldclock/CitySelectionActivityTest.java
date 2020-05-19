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

package com.android.deskclock.worldclock;

import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.appcompat.widget.SearchView;
import androidx.test.InstrumentationRegistry;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.rule.ActivityTestRule;

import com.android.deskclock.R;
import com.android.deskclock.data.City;
import com.android.deskclock.data.DataModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Exercise the user interface that adjusts the selected cities.
 */
@RunWith(AndroidJUnit4ClassRunner.class)
public class CitySelectionActivityTest {

    private CitySelectionActivity activity;
    private ListView cities;
    private ListAdapter citiesAdapter;
    private SearchView searchView;
    private Locale defaultLocale;

    @Rule
    public ActivityTestRule<CitySelectionActivity> rule =
            new ActivityTestRule<>(CitySelectionActivity.class, true);

    @Before
    public void setUp() {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(new Locale("en", "US"));
        Runnable setUpRunnable = () -> {
            final City city = DataModel.getDataModel().getAllCities().get(0);
            DataModel.getDataModel().setSelectedCities(Collections.singletonList(city));
            activity = rule.getActivity();
            cities = activity.findViewById(R.id.cities_list);
            citiesAdapter = (ListAdapter) cities.getAdapter();
            searchView = activity.findViewById(R.id.menu_item_search);
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(setUpRunnable);
    }

    @After
    public void tearDown() {
        activity = null;
        cities = null;
        searchView = null;
        Locale.setDefault(defaultLocale);
    }

    @Test
    public void validateDefaultState() {
        Runnable testRunable = () -> {
            assertNotNull(searchView);
            assertNotNull(cities);
            assertNotNull(citiesAdapter);
            assertEquals(340, citiesAdapter.getCount());
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(testRunable);
    }

    @Test
    public void searchCities() {
        Runnable testRunable = () -> {
            // Search for cities starting with Z.
            searchView.setQuery("Z", true);
            assertEquals(2, citiesAdapter.getCount());
            assertItemContent(0, "Zagreb");
            assertItemContent(1, "Zurich");

            // Clear the filter query.
            searchView.setQuery("", true);
            assertEquals(340, citiesAdapter.getCount());
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(testRunable);
    }

    private void assertItemContent(int index, String cityName) {
        final City city = (City) citiesAdapter.getItem(index);
        assertEquals(cityName, city.getName());
    }
}
