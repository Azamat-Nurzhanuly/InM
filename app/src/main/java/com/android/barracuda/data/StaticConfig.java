package com.android.barracuda.data;


public class StaticConfig {
  public static final boolean TEST_MODE = true;

  public static int REQUEST_CODE_REGISTER = 2000;
  public static String STR_EXTRA_ACTION_LOGIN = "login";
  public static String STR_EXTRA_ACTION_RESET = "resetpass";
  public static String STR_EXTRA_ACTION = "action";
  public static String STR_EXTRA_USERNAME = "username";
  public static String STR_EXTRA_PASSWORD = "password";
  public static String STR_DEFAULT_BASE64 = "default";
  public static String UID = TEST_MODE ? "1512615488866778" : null; //Zhandos
  //    public static String UID = "803619516500076"; //Khamit
//  //  public static String UID = "199658337315413"; //Aza
  //TODO only use this UID for debug mode
//    public static String UID = "6kU0SbJPF5QJKZTfvW1BqKolrx22";
  public static String INTENT_KEY_CHAT_FRIEND = "friendname";
  public static String INTENT_KEY_CHAT_AVATA = "friendavata";
  public static String INTENT_KEY_CHAT_ID = "friendid";
  public static String INTENT_KEY_CHAT_ROOM_ID = "roomid";
  public static long TIME_TO_REFRESH = 10 * 1000;
  public static long TIME_TO_OFFLINE = 2 * 60 * 1000;

  //sinch
  public static String SINCH_KEY = "b77572b7-a9cc-4dc8-b7ad-09254cf7182d";
  public static String SINCH_SECRET = "PkUMsoJsDk+8uGzCEerM5A==";
  public static String SINCH_HOST = "clientapi.sinch.com";

  public static final long KEY_LIFETIME = TEST_MODE ? (2 * 60 * 1000) : (24 * 60 * 60 * 1000);
  public static final long ASTANA_OFFSET = 6 * (60 * 60 * 1000);
  //type of call

  public final static String CALL_OUTGOING = "outgoing";
  public final static String CALL_INCOMING = "incoming";

}
