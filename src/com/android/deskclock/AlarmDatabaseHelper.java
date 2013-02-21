/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

/**
 * Helper class for opening the database from multiple providers.  Also provides
 * some common functionality.
 */
class AlarmDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "alarms.db";
    private static final int DATABASE_VERSION = 6;

    private static final String FIELDS =
                                "hour,minutes,daysofweek,alarmtime," +
                                "enabled,vibrate,message,alert,incvol";
    private static final String ALL_FIELDS = "_id," + FIELDS;

    public AlarmDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createAlarmsTable(db, false);

        // insert default alarms
        String insertMe = "INSERT INTO alarms (" + FIELDS + ") VALUES ";
        db.execSQL(insertMe + "(8, 30, 31, 0, 0, 1, '', '', 0);");
        db.execSQL(insertMe + "(9, 00, 96, 0, 0, 1, '', '', 0);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int currentVersion) {
        if (Log.LOGV) Log.v("Upgrading alarms database from version " + oldVersion + " to "
                + currentVersion);

        int upgradeVersion = oldVersion;

        if (upgradeVersion == 5) {
            db.execSQL("ALTER TABLE alarms ADD incvol INTEGER;");
            db.execSQL("UPDATE alarms SET incvol=0;");
            upgradeVersion = 6;
        }

        if (Log.LOGV) Log.v("Alarms database upgrade done.");
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int currentVersion) {

        if (Log.LOGV) Log.v("Downgrading alarms database from version " + oldVersion + " to "
                + currentVersion);

        db.execSQL("BEGIN TRANSACTION;");
        createAlarmsTable(db, true);
        db.execSQL("INSERT INTO alarms_tmp SELECT " + ALL_FIELDS + " FROM alarms;");
        db.execSQL("DROP TABLE alarms;");
        createAlarmsTable(db, false);
        db.execSQL("INSERT INTO alarms SELECT " + ALL_FIELDS + " FROM alarms_tmp;");
        db.execSQL("DROP TABLE alarms_tmp;");
        db.execSQL("COMMIT;");

        if (Log.LOGV) Log.v("Alarms database downgrade done.");
    }

    private void createAlarmsTable(SQLiteDatabase db, boolean temporary) {
        String temporaryClause = (temporary) ? "TEMPORARY" : "";
        String tableName = (temporary) ? "alarms_tmp" : "alarms";
        db.execSQL("CREATE " + temporaryClause + " TABLE " + tableName + " (" +
                   "_id INTEGER PRIMARY KEY," +
                   "hour INTEGER, " +
                   "minutes INTEGER, " +
                   "daysofweek INTEGER, " +
                   "alarmtime INTEGER, " +
                   "enabled INTEGER, " +
                   "vibrate INTEGER, " +
                   "message TEXT, " +
                   "alert TEXT, " +
                   "incvol INTEGER);");
    }

    Uri commonInsert(ContentValues values) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        long rowId = -1;
        try {
            // Check if we are trying to re-use an existing id.
            Object value = values.get(Alarm.Columns._ID);
            if (value != null) {
                int id = (Integer) value;
                if (id > -1) {
                    final Cursor cursor = db
                            .query("alarms", new String[]{Alarm.Columns._ID}, "_id = ?",
                                    new String[]{id + ""}, null, null, null);
                    if (cursor.moveToFirst()) {
                        // Record exists. Remove the id so sqlite can generate a new one.
                        values.putNull(Alarm.Columns._ID);
                    }
                }
            }

            rowId = db.insert("alarms", Alarm.Columns.MESSAGE, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (rowId < 0) {
            throw new SQLException("Failed to insert row");
        }
        if (Log.LOGV) Log.v("Added alarm rowId = " + rowId);

        return ContentUris.withAppendedId(Alarm.Columns.CONTENT_URI, rowId);
    }
}
