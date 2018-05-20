package com.android.barracuda.cypher;

import android.content.Context;
import android.util.Log;
import com.android.barracuda.data.FBaseEntities;
import com.android.barracuda.data.PublicKeysDB;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.cypher.Key;
import com.android.barracuda.model.cypher.PublicKeys;
import com.google.firebase.database.FirebaseDatabase;

import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

public class PublicKeyWorker {

  public static void updatePublicKeys(Context context) {
    if (context == null) throw new NullPointerException("Context can not be null");
    long now = new Date().getTime();
    Key keys = PublicKeysDB.getInstance(context).getKey(now);

    if (keys == null || keys.timestamp + StaticConfig.KEY_LIFETIME < now) {
      try {
        registerNewPublicKey(context);
      } catch (NoSuchAlgorithmException e) {
        Log.e("PublicKeyWorker", "Cant create key", e);
      }
    }
  }

  private static void registerNewPublicKey(Context context) throws NoSuchAlgorithmException {
    PublicKeys pks = new PublicKeys();

    KeyPairGenerator kpg = KeyPairGenerator.getInstance("DiffieHellman");
    kpg.initialize(512);
    KeyPair kp = kpg.generateKeyPair();
    DHParameterSpec params = ((DHPublicKey) kp.getPublic()).getParams();

    pks.g = params.getG().toString();
    pks.p = params.getP().toString();
    pks.key = (((javax.crypto.interfaces.DHPublicKey) kp.getPublic()).getY()).toString();

    String privateKey = (((javax.crypto.interfaces.DHPrivateKey) kp.getPrivate()).getX()).toString();

    FirebaseDatabase.getInstance().getReference()
      .child(FBaseEntities.PUBLIC_KEYS + FBaseEntities.DELIM + StaticConfig.UID).push().setValue(pks);

    PublicKeysDB.getInstance(context).setKey(pks, privateKey);
  }
}
