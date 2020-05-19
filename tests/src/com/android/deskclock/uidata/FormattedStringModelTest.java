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

package com.android.deskclock.uidata;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4ClassRunner.class)
public class FormattedStringModelTest {

    private FormattedStringModel model;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        model = new FormattedStringModel(context);
    }

    @After
    public void tearDown() {
        model = null;
    }

    @Test
    public void positiveFormattedNumberWithNoPadding() {
        assertEquals("0", model.getFormattedNumber(0));
        assertEquals("9", model.getFormattedNumber(9));
        assertEquals("10", model.getFormattedNumber(10));
        assertEquals("99", model.getFormattedNumber(99));
        assertEquals("100", model.getFormattedNumber(100));
    }

    @Test
    public void positiveFormattedNumber() {
        assertEquals("0", model.getFormattedNumber(false, 0, 1));
        assertEquals("00", model.getFormattedNumber(false, 0, 2));
        assertEquals("000", model.getFormattedNumber(false, 0, 3));

        assertEquals("9", model.getFormattedNumber(false, 9, 1));
        assertEquals("09", model.getFormattedNumber(false, 9, 2));
        assertEquals("009", model.getFormattedNumber(false, 9, 3));

        assertEquals("90", model.getFormattedNumber(false, 90, 2));
        assertEquals("090", model.getFormattedNumber(false, 90, 3));

        assertEquals("999", model.getFormattedNumber(false, 999, 3));
    }

    @Test
    public void negativeFormattedNumber() {
        assertEquals("−0", model.getFormattedNumber(true, 0, 1));
        assertEquals("−00", model.getFormattedNumber(true, 0, 2));
        assertEquals("−000", model.getFormattedNumber(true, 0, 3));

        assertEquals("−9", model.getFormattedNumber(true, 9, 1));
        assertEquals("−09", model.getFormattedNumber(true, 9, 2));
        assertEquals("−009", model.getFormattedNumber(true, 9, 3));

        assertEquals("−90", model.getFormattedNumber(true, 90, 2));
        assertEquals("−090", model.getFormattedNumber(true, 90, 3));

        assertEquals("−999", model.getFormattedNumber(true, 999, 3));
    }
}
