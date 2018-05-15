package com.android.barracuda.util;

import com.android.barracuda.data.StaticConfig;

/**
 * Created by Khamit Mateyev on 5/15/18.
 */
public class AuthUtils {
  public static String userIdToRoomId(String userId) {
    return userId.compareTo(StaticConfig.UID) > 0 ? (StaticConfig.UID + userId).hashCode() + "" : "" + (userId + StaticConfig.UID).hashCode();
  }
}
