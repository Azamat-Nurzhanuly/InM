package com.android.barracuda.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import com.android.barracuda.cypher.models.Key;

import java.math.BigInteger;
import java.util.ArrayList;

import static com.android.barracuda.data.KeyStorageDB.TableStruct.*;

public final class KeyStorageDB {
  private static KeyStorageDBHelper mDbHelper = null;

  private KeyStorageDB() {
  }

  private static KeyStorageDB instance = null;

  public static KeyStorageDB getInstance(Context context) {
    if (instance == null) {
      instance = new KeyStorageDB();
      mDbHelper = new KeyStorageDBHelper(context);
      instance.createTable();
    }
    return instance;
  }

  public void addKey(Key key) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();

    ContentValues values = new ContentValues();
    values.put(PUB_KEY_TS, key.friendKeyTs);
    values.put(ROOM_ID, key.roomId);
    values.put(FRIEND_ID, key.friendId);
    if (key.pubKey != null) values.put(PUB_KEY, key.pubKey.toString());
    if (key.ownPubKey != null) values.put(OWN_PUB_KEY, key.ownPubKey.toString());
    values.put(KEY, key.key.toString());
    values.put(TIMESTAMP, key.timestamp);

    db.insert(TABLE_NAME, null, values);
  }

  public BigInteger getSecretKey(long friendPubKeyTs, String roomId) {
    try (SQLiteDatabase db = mDbHelper.getReadableDatabase()) {
      String sql = "select " + KEY + " from " + TABLE_NAME + " where " + PUB_KEY_TS + "=? and " + ROOM_ID + "=?";
      try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(friendPubKeyTs), roomId})) {
        if (cursor.moveToNext()) return new BigInteger(cursor.getString(0));
      }
    }

    return null;
  }

  public Key getKey(long friendKeyTs, String roomId) {
    try (SQLiteDatabase db = mDbHelper.getReadableDatabase()) {
      String sql = "select * from " + TABLE_NAME + " where " + PUB_KEY_TS + "=? and " + ROOM_ID + "=?";
      try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(friendKeyTs), roomId})) {
        if (cursor.moveToNext()) {
          return extractKeyFromCursor(cursor);
        } else {
          return null;
        }
      }
    }
  }

  public Key getLastKeyForRoom(String roomId) {
    try (SQLiteDatabase db = mDbHelper.getReadableDatabase()) {
      String sql = "select * from " + TABLE_NAME + " where " + ROOM_ID + "=? order by " + PUB_KEY_TS + " desc limit 1";
      try (Cursor cursor = db.rawQuery(sql, new String[]{roomId})) {
        if (cursor.moveToNext()) {
          return extractKeyFromCursor(cursor);
        } else {
          return null;
        }
      }
    }
  }

  private Key extractKeyFromCursor(Cursor cursor) {
    Key key = new Key();
    int i = 0;
    key.friendKeyTs = cursor.getLong(i++);
    key.roomId = cursor.getString(i++);
    key.friendId = cursor.getString(i++);

    String pubKey = cursor.getString(i++);
    if (pubKey != null)
      key.pubKey = new BigInteger(pubKey);

    String ownPubKey = cursor.getString(i++);
    if (ownPubKey != null)
      key.ownPubKey = new BigInteger(ownPubKey);

    key.key = new BigInteger(cursor.getString(i++));
    key.timestamp = cursor.getLong(i);
    return key;
  }

  public static class TableStruct implements BaseColumns {
    static final String TABLE_NAME = "key_storage";

    static final String PUB_KEY_TS = "pub_key_ts";
    static final String ROOM_ID = "room_id";
    static final String FRIEND_ID = "friend_id";
    static final String PUB_KEY = "public_key";
    static final String OWN_PUB_KEY = "own_pub_key";
    static final String KEY = "sec_key";
    static final String TIMESTAMP = "timestamp";
  }

  private static final String SQL_DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME + ";";

  private void dropTable() {
    try (SQLiteDatabase db = mDbHelper.getReadableDatabase()) {
      db.execSQL(SQL_DROP_TABLE);
    }
  }

  private void createTable() {
    try (SQLiteDatabase db = mDbHelper.getReadableDatabase()) {
      db.execSQL(SQL_CREATE_TABLE);
    }
  }

  private static final String SQL_CREATE_TABLE =
    "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + "(" +
      PUB_KEY_TS + " INTEGER," +
      ROOM_ID + " TEXT," +
      FRIEND_ID + " TEXT," +
      PUB_KEY + " TEXT," +
      OWN_PUB_KEY + " TEXT," +
      KEY + " TEXT," +
      TIMESTAMP + " INTEGER," +
      "PRIMARY KEY(" + PUB_KEY_TS + "," + ROOM_ID + ")" +
      ");";

  private static class KeyStorageDBHelper extends SQLiteOpenHelper {
    static final int DATABASE_VERSION = 1;
    static final String DATABASE_NAME = "KeyStorageChat.db";

    KeyStorageDBHelper(Context context) {
      super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
      db.execSQL(SQL_CREATE_TABLE);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      onUpgrade(db, oldVersion, newVersion);
    }
  }
}
