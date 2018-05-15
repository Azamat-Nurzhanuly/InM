package com.android.barracuda.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import com.android.barracuda.model.Key;

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
    }
    return instance;
  }


  public long addKey(Key key) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();

    ContentValues values = new ContentValues();
    values.put(TableStruct.ROOM_ID, key.roomId);
    values.put(TableStruct.USER_ID, key.userId);
    values.put(TableStruct.P, key.p.toString());
    values.put(TableStruct.G, key.g.toString());
    values.put(TableStruct.PUB_KEY, key.pubKey.toString());
    values.put(TableStruct.OWN_P, key.ownP.toString());
    values.put(TableStruct.OWN_G, key.ownG.toString());
    values.put(TableStruct.OWN_PUB_KEY, key.ownPubKey.toString());
    values.put(TableStruct.OWN_PRV_KEY, key.ownPrvKey.toString());
    values.put(TableStruct.KEY, key.key.toString());
    values.put(TableStruct.TIMESTAMP, key.timestamp);

    return db.insertWithOnConflict(TableStruct.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
  }

  public ArrayList<Key> getKeyList() {
    ArrayList<Key> list = new ArrayList<>();

    try (SQLiteDatabase db = mDbHelper.getReadableDatabase()) {
      try (Cursor cursor = db.rawQuery("select * from " + TableStruct.TABLE_NAME, null)) {
        while (cursor.moveToNext()) {
          Key key = new Key();
          int i = 0;
          key.roomId = cursor.getString(i++);
          key.userId = cursor.getString(i++);
          key.p = new BigInteger(cursor.getString(i++));
          key.g = new BigInteger(cursor.getString(i++));
          key.pubKey = new BigInteger(cursor.getString(i++));
          key.ownP = new BigInteger(cursor.getString(i++));
          key.ownG = new BigInteger(cursor.getString(i++));
          key.ownPubKey = new BigInteger(cursor.getString(i++));
          key.ownPrvKey = new BigInteger(cursor.getString(i++));
          key.key = new BigInteger(cursor.getString(i++));
          key.timestamp = cursor.getLong(i++);

          list.add(key);
        }
      } catch (Exception e) {
        return new ArrayList<>();
      }
    }

    return list;
  }

  public static class TableStruct implements BaseColumns {
    static final String TABLE_NAME = "key_storage";

    static final String ROOM_ID = "room_id";
    static final String USER_ID = "user_id";

    static final String P = "p";
    static final String G = "g";
    static final String PUB_KEY = "public_key";

    static final String OWN_P = "own_p";
    static final String OWN_G = "own_g";
    static final String OWN_PUB_KEY = "own_pub_key";
    static final String OWN_PRV_KEY = "own_prv_key";

    static final String KEY = "key";

    static final String TIMESTAMP = "timestamp";

  }

  private static final String SQL_CREATE_TABLE =
    "CREATE TABLE IF NOT EXISTS " + TableStruct.TABLE_NAME + "(" +
      TableStruct.ROOM_ID + " TEXT," +
      TableStruct.USER_ID + " TEXT," +
      TableStruct.P + " TEXT," +
      TableStruct.G + " TEXT," +
      TableStruct.PUB_KEY + " TEXT," +
      TableStruct.OWN_P + " TEXT," +
      TableStruct.OWN_G + " TEXT," +
      TableStruct.OWN_PUB_KEY + " TEXT," +
      TableStruct.OWN_PRV_KEY + " TEXT," +
      TableStruct.KEY + " TEXT," +
      TableStruct.TIMESTAMP + " INTEGER" +
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
