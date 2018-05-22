package com.android.barracuda.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import com.android.barracuda.model.Call;
import com.android.barracuda.model.ListCall;

public final class CallDB {
  private static CallDBHelper mDbHelper = null;

  // To prevent someone from accidentally instantiating the contract class,
  // make the constructor private.
  private CallDB() {
  }

  private static CallDB instance = null;

  public static CallDB getInstance(Context context) {
    if (instance == null) {
      instance = new CallDB();
      mDbHelper = new CallDBHelper(context);
    }
    return instance;
  }


  public long addCall(Call call) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    // Create a new map of values, where column names are the keys
    ContentValues values = new ContentValues();
    values.put(FeedEntry.COLUMN_NAME_ID, call.callId);
    values.put(FeedEntry.COLUMN_NAME_FRIEND_ID, call.id);
    values.put(FeedEntry.COLUMN_NAME_NAME, call.name);
    values.put(FeedEntry.COLUMN_NAME_PHONE_NUMBER, call.phoneNumber);
    values.put(FeedEntry.COLUMN_NAME_AVATA, call.avata);
    values.put(FeedEntry.COLUMN_NAME_TYPE, call.type);
    // Insert the new row, returning the primary key value of the new row
    return db.insert(FeedEntry.TABLE_NAME, null, values);
  }


  public void addListCall(ListCall listCall) {
    for (Call call : listCall.getListCall()) {
      addCall(call);
    }
  }

  public ListCall getListCall() {
    ListCall listCall = new ListCall();
    SQLiteDatabase db = mDbHelper.getReadableDatabase();
    // Define a projection that specifies which columns from the database
// you will actually use after this query.
    try {
      Cursor cursor = db.rawQuery("select * from " + FeedEntry.TABLE_NAME, null);
      while (cursor.moveToNext()) {
        Call call = new Call();
        call.callId = cursor.getString(0);
        call.id = cursor.getString(1);
        call.name = cursor.getString(2);
        call.phoneNumber = cursor.getString(3);
        call.type = cursor.getString(4);
        call.avata = cursor.getString(5);
        listCall.getListCall().add(call);
      }
      cursor.close();
    } catch (Exception e) {
      return new ListCall();
    }
    return listCall;
  }

  public void dropDB() {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    db.execSQL(SQL_DELETE_ENTRIES);
    db.execSQL(SQL_CREATE_ENTRIES);
  }

  /* Inner class that defines the table contents */
  public static class FeedEntry implements BaseColumns {
    static final String TABLE_NAME = "calls";
    static final String COLUMN_NAME_ID = "call_id";
    static final String COLUMN_NAME_FRIEND_ID = "friend_id";
    static final String COLUMN_NAME_NAME = "name";
    static final String COLUMN_NAME_PHONE_NUMBER = "phoneNumber";
    static final String COLUMN_NAME_AVATA = "avata";
    static final String COLUMN_NAME_TYPE = "type";
  }

  private static final String TEXT_TYPE = " TEXT";
  private static final String COMMA_SEP = ",";
  private static final String SQL_CREATE_ENTRIES =
    "CREATE TABLE " + FeedEntry.TABLE_NAME + " (" +
      FeedEntry.COLUMN_NAME_ID + " TEXT PRIMARY KEY," +
      FeedEntry.COLUMN_NAME_FRIEND_ID + TEXT_TYPE + COMMA_SEP +
      FeedEntry.COLUMN_NAME_NAME + TEXT_TYPE + COMMA_SEP +
      FeedEntry.COLUMN_NAME_PHONE_NUMBER + TEXT_TYPE + COMMA_SEP +
      FeedEntry.COLUMN_NAME_TYPE + TEXT_TYPE + COMMA_SEP +
      FeedEntry.COLUMN_NAME_AVATA + TEXT_TYPE + " )";

  private static final String SQL_DELETE_ENTRIES =
    "DROP TABLE IF EXISTS " + FeedEntry.TABLE_NAME;


  private static class CallDBHelper extends SQLiteOpenHelper {
    // If you change the database schema, you must increment the database version.
    static final int DATABASE_VERSION = 1;
    static final String DATABASE_NAME = "CallList.db";

    CallDBHelper(Context context) {
      super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
      db.execSQL(SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      // This database is only a cache for online data, so its upgrade policy is
      // to simply to discard the data and start over
      db.execSQL(SQL_DELETE_ENTRIES);
      onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      onUpgrade(db, oldVersion, newVersion);
    }
  }
}
