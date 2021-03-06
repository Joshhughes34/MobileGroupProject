package com.mobilegroupproject.studentorganiser.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class EventsDbHelper extends SQLiteOpenHelper{

    public static final int DB_VERSION = 1;
    // db name as public as we use it in test later
    public static final String DB_NAME = "events.db";

    public SQLiteDatabase myDB;

    public EventsDbHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, null, version);
        myDB = getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        myDB = db;

        String query = "CREATE TABLE IF NOT EXISTS EVENTS_TABLE ( _ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "TITLE TEXT," +
                "DATE TEXT," +
                "STARTTIME TEXT," +
                "ENDTIME TEXT," +
                "BUILDING TEXT," +
                "HANGOUT_LINK TEXT," +
                "CREATOR TEXT," +
                "COLOUR_ID TEXT," +
                "DESCRIPTION TEXT," +
                "UID TEXT);";

        db.execSQL(query);

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        db.execSQL("DROP TABLE IF EXISTS EVENTS_TABLE");

        onCreate(db);
    }


    public void clearTable(String table_name){
        myDB.execSQL("DELETE FROM "+ table_name);
    }
}