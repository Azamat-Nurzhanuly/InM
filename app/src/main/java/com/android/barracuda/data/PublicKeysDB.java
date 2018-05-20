package com.android.barracuda.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import com.android.barracuda.model.cypher.Key;
import com.android.barracuda.model.cypher.PublicKeys;

import java.math.BigInteger;
import java.util.Date;

public final class PublicKeysDB {
  private static PublicKeysDBHelper mDbHelper = null;

  private PublicKeysDB() {
  }

  private static PublicKeysDB instance = null;

  public static PublicKeysDB getInstance(Context context) {
    if (instance == null) {
      instance = new PublicKeysDB();
      mDbHelper = new PublicKeysDBHelper(context);
    }
    return instance;
  }

  public void setKey(PublicKeys keys, String privateKey) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();

    ContentValues values = new ContentValues();
    values.put(TableStruct.ID, 2);
    values.put(TableStruct.P, keys.p);
    values.put(TableStruct.G, keys.g);
    values.put(TableStruct.PUB_KEY, keys.key);
    values.put(TableStruct.PRV_KEY, privateKey);
    values.put(TableStruct.TIMESTAMP, new Date().getTime());

    ContentValues toUpdate = new ContentValues();
    toUpdate.put(TableStruct.ID, 1);

    db.delete(TableStruct.TABLE_NAME, "id=1", null);
    db.update(TableStruct.TABLE_NAME, toUpdate, "id=2", null);
    db.insert(TableStruct.TABLE_NAME, null, values);
  }

  public Key getKey(long timestamp) {
    Key key = null;
    try (SQLiteDatabase db = mDbHelper.getReadableDatabase()) {
      String sql = "select " +
        TableStruct.P + "," + TableStruct.G + "," + TableStruct.PUB_KEY + "," + TableStruct.PRV_KEY + ", " + TableStruct.TIMESTAMP +
        " from " + TableStruct.TABLE_NAME + " where timestamp < " + timestamp;
      try (Cursor cursor = db.rawQuery(sql, null)) {
        if (cursor.moveToNext()) {
          key = new Key();
          int i = 0;

          key.p = new BigInteger(cursor.getString(i++));
          key.g = new BigInteger(cursor.getString(i++));
          key.ownPubKey = new BigInteger(cursor.getString(i++));
          key.ownPrvKey = new BigInteger(cursor.getString(i++));
          key.timestamp = cursor.getLong(i);
        }
      }
    }
    return key;
  }

  private static class TableStruct implements BaseColumns {
    static final String TABLE_NAME = "public_keys";

    static final String ID = "id";
    static final String P = "p";
    static final String G = "g";
    static final String PUB_KEY = "public_key";
    static final String PRV_KEY = "prv_key";
    static final String TIMESTAMP = "timestamp";
  }

  private static final String SQL_CREATE_TABLE =
    "CREATE TABLE IF NOT EXISTS " + TableStruct.TABLE_NAME + "(" +
      TableStruct.ID + " INTEGER primary key," +
      TableStruct.P + " TEXT," +
      TableStruct.G + " TEXT," +
      TableStruct.PUB_KEY + " TEXT," +
      TableStruct.PRV_KEY + " TEXT," +
      TableStruct.TIMESTAMP + " INTEGER" +
      ")";

  private static class PublicKeysDBHelper extends SQLiteOpenHelper {
    static final int DATABASE_VERSION = 1;
    static final String DATABASE_NAME = "PublicKeysChat.db";

    PublicKeysDBHelper(Context context) {
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
