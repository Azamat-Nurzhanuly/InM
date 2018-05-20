package com.android.barracuda.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import com.android.barracuda.model.cypher.Key;

import java.math.BigInteger;
import java.util.ArrayList;

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


  public long addKey(Key key) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();

    ContentValues values = new ContentValues();
    values.put(TableStruct.ROOM_ID, key.roomId);
    values.put(TableStruct.USER_ID, key.userId);
    values.put(TableStruct.PUB_KEY, key.pubKey.toString());
    values.put(TableStruct.OWN_PUB_KEY, key.ownPubKey.toString());
    values.put(TableStruct.KEY, key.key.toString());
    values.put(TableStruct.TIMESTAMP, key.timestamp);

    return db.insert(TableStruct.TABLE_NAME, null, values);
  }

  public ArrayList<Key> getKeyList() {
    ArrayList<Key> list = new ArrayList<>();

    try (SQLiteDatabase db = mDbHelper.getReadableDatabase()) {
      try (Cursor cursor = db.rawQuery("select " + TableStruct.ROOM_ID + ", " + TableStruct.USER_ID + ", "
        + TableStruct.PUB_KEY + ", " + TableStruct.OWN_PUB_KEY + ", " + TableStruct.KEY + ", "
        + TableStruct.TIMESTAMP +
        " from " + TableStruct.TABLE_NAME, null)) {
        while (cursor.moveToNext()) {
          Key key = new Key();
          int i = 0;
          key.roomId = cursor.getString(i++);
          key.userId = cursor.getString(i++);
          key.pubKey = new BigInteger(cursor.getString(i++));
          key.ownPubKey = new BigInteger(cursor.getString(i++));
          key.key = new BigInteger(cursor.getString(i++));
          key.timestamp = cursor.getLong(i);

          list.add(key);
        }
      }
    }

    return list;
  }

  public BigInteger getSecretKeyByFriendsPublic(String friendPublicKey) {
    try (SQLiteDatabase db = mDbHelper.getReadableDatabase()) {
      String sql = "select " + TableStruct.KEY + " from " + TableStruct.TABLE_NAME + " where " + TableStruct.PUB_KEY + "=?";
      try (Cursor cursor = db.rawQuery(sql, new String[]{friendPublicKey})) {
        if (cursor.moveToNext()) return new BigInteger(cursor.getString(0));
      }
    }

    return null;
  }

  public Key getKeyByFriendsPublic(String friendPublicKey) {
    Key key = null;
    try (SQLiteDatabase db = mDbHelper.getReadableDatabase()) {
      String sql = "select " + TableStruct.KEY + ", " + TableStruct.OWN_PUB_KEY + ", " + TableStruct.PUB_KEY + ", timestamp" +
        " from " + TableStruct.TABLE_NAME + " where " + TableStruct.PUB_KEY + "=?";
      try (Cursor cursor = db.rawQuery(sql, new String[]{friendPublicKey})) {
        if (cursor.moveToNext()) {
          key = new Key();
          key.key = new BigInteger(cursor.getString(0));
          key.ownPubKey = new BigInteger(cursor.getString(1));
          key.pubKey = new BigInteger(cursor.getString(2));
          key.timestamp = cursor.getLong(3);
        }
      }
    }

    return key;
  }

  public Key getKeyByOwnPkAndRoomId(String ownPk, String roomId) {
    Key key = null;
    try (SQLiteDatabase db = mDbHelper.getReadableDatabase()) {
      String sql = "select " + TableStruct.KEY + ", " + TableStruct.OWN_PUB_KEY + ", " + TableStruct.PUB_KEY + ", timestamp" +
        " from " + TableStruct.TABLE_NAME + " where " + TableStruct.OWN_PUB_KEY + "=? and " + TableStruct.ROOM_ID + "=?";
      try (Cursor cursor = db.rawQuery(sql, new String[]{ownPk, roomId})) {
        if (cursor.moveToNext()) {
          key = new Key();
          key.key = new BigInteger(cursor.getString(0));
          key.ownPubKey = new BigInteger(cursor.getString(1));
          key.pubKey = new BigInteger(cursor.getString(2));
          key.timestamp = cursor.getLong(3);
        }
      }
    }

    return key;
  }

  public BigInteger getSecretKeyByMyPublic(String myPublicKey) {
    try (SQLiteDatabase db = mDbHelper.getReadableDatabase()) {
      String sql = "select " + TableStruct.KEY + " from " + TableStruct.TABLE_NAME + " where " + TableStruct.OWN_PUB_KEY + "=?";
      try (Cursor cursor = db.rawQuery(sql, new String[]{myPublicKey})) {
        if (cursor.moveToNext()) return new BigInteger(cursor.getString(0));
      }
    }
    return null;
  }

  public Key getSecretKeyForRoom(String roomId) {
    try (SQLiteDatabase db = mDbHelper.getReadableDatabase()) {
      String joinTable = "(select max(timestamp) as ts from " + TableStruct.TABLE_NAME + " where " + TableStruct.ROOM_ID + "=?) jt";
      String sql = "select " + TableStruct.KEY + ", " + TableStruct.OWN_PUB_KEY + ", " + TableStruct.PUB_KEY + ", timestamp from " + TableStruct.TABLE_NAME + " t" +
        " inner join " + joinTable + " on jt.ts = t.timestamp";

      try (Cursor cursor = db.rawQuery(sql, new String[]{roomId})) {
        if (cursor.moveToNext()) {
          Key key = new Key();
          key.key = new BigInteger(cursor.getString(0));
          key.ownPubKey = new BigInteger(cursor.getString(1));
          key.pubKey = new BigInteger(cursor.getString(2));
          key.timestamp = cursor.getLong(3);
          return key;
        }
      }
    }

    return null;
  }

  public static class TableStruct implements BaseColumns {
    static final String TABLE_NAME = "key_storage";

    static final String ROOM_ID = "room_id";
    static final String USER_ID = "user_id";

    static final String PUB_KEY = "public_key";

    static final String OWN_PUB_KEY = "own_pub_key";

    static final String KEY = "sec_key";

    static final String TIMESTAMP = "timestamp";

  }

  private static final String SQL_DROP_TABLE = "DROP TABLE IF EXISTS " + TableStruct.TABLE_NAME + ";";

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
    "CREATE TABLE IF NOT EXISTS " + TableStruct.TABLE_NAME + "(" +
      TableStruct.ROOM_ID + " TEXT," +
      TableStruct.USER_ID + " TEXT," +
      TableStruct.PUB_KEY + " TEXT," +
      TableStruct.OWN_PUB_KEY + " TEXT," +
      TableStruct.KEY + " TEXT," +
      TableStruct.TIMESTAMP + " INTEGER primary key" +
      ")";

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
