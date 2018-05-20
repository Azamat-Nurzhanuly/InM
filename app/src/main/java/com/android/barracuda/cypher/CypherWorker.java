package com.android.barracuda.cypher;

import android.content.Context;
import android.util.Base64;
import com.android.barracuda.cypher.exceptions.NoKeyException;
import com.android.barracuda.data.FBaseEntities;
import com.android.barracuda.data.KeyStorageDB;
import com.android.barracuda.data.PublicKeysDB;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.Message;
import com.android.barracuda.model.cypher.Key;
import com.android.barracuda.model.cypher.PublicKeys;
import com.android.barracuda.util.AuthUtils;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import javax.crypto.Cipher;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;

public class CypherWorker {

  public static void encryptAndSend(final Message msg, final Context context) {
    final Key key = KeyStorageDB.getInstance(context).getSecretKeyForRoom(msg.idReceiver);

    FirebaseDatabase.getInstance().getReference().child(FBaseEntities.PUBLIC_KEYS + FBaseEntities.DELIM + msg.friendId)
      .orderByKey().limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) throws RuntimeException {
        HashMap map = ((HashMap) dataSnapshot.getValue());
        if (map == null || map.isEmpty()) return;

        HashMap value = (HashMap) map.values().iterator().next();
        PublicKeys pk = new PublicKeys();
        pk.key = (String) value.get("key");
        pk.p = (String) value.get("p");
        pk.g = (String) value.get("g");

        if (pk.key == null) return;
        try {
          if (key == null || !pk.key.equals(key.pubKey.toString())) {
            Key newKey = handlePublicKey(msg.friendId, pk, context);
            doEncrypt(msg, newKey);
          } else {
            doEncrypt(msg, key);
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        FirebaseDatabase.getInstance().getReference().child("message/" + msg.idReceiver).push().setValue(msg);
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {}
    });
  }

  public static void decrypt(Message msg, Context context) throws NoKeyException {
    if (msg.key == null) return;
    BigInteger secretKey;

    if (msg.idSender.equals(StaticConfig.UID)) {
      secretKey = KeyStorageDB.getInstance(context).getSecretKeyByMyPublic(msg.key);
    } else {
      secretKey = KeyStorageDB.getInstance(context).getSecretKeyFor(msg.key);

      if (secretKey == null) {
        BigInteger friendPublicKey = new BigInteger(msg.key);

        Key keys = PublicKeysDB.getInstance(context).getKey(msg.timestamp);

        secretKey = new BigInteger(subArray(calcSharedSecretKey(keys.p, keys.ownPrvKey, friendPublicKey).toByteArray(), 0, 256 / 8));

        Key key = new Key();
        key.roomId = msg.idReceiver;
        key.p = keys.p;
        key.g = keys.g;
        key.pubKey = friendPublicKey;
        key.ownPubKey = keys.ownPubKey;
        key.ownPrvKey = keys.ownPrvKey;
        key.key = secretKey;
        key.timestamp = msg.timestamp;

        KeyStorageDB.getInstance(context).addKey(key);
      }
    }
    if (secretKey == null) throw new NoKeyException("No key");

    try {
      msg.text = decryptText(msg.text, secretKey.toByteArray()) + "\nEncrypted: " + msg.text;
    } catch (Exception e) {
      if (e instanceof NoKeyException) throw (NoKeyException) e;
      e.printStackTrace();
    }

  }

  private static BigInteger calcSharedSecretKey(BigInteger p, BigInteger privateKey, BigInteger friendPubKey) {
    return friendPubKey.modPow(privateKey, p);
  }

  private static Key handlePublicKey(String userId, PublicKeys keys, Context context) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
    Key k = new Key();
    BigInteger p = new BigInteger(keys.p);
    BigInteger g = new BigInteger(keys.g);
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("DiffieHellman");
    DHParameterSpec param = new DHParameterSpec(p, g);
    kpg.initialize(param);
    KeyPair dkp = kpg.generateKeyPair();

    BigInteger publicKey = ((javax.crypto.interfaces.DHPublicKey) dkp.getPublic()).getY();
    BigInteger privateKey = ((javax.crypto.interfaces.DHPrivateKey) dkp.getPrivate()).getX();
    BigInteger firendPubKey = new BigInteger(keys.key);
    BigInteger secretKey = new BigInteger(subArray(calcSharedSecretKey(p, privateKey, firendPubKey).toByteArray(), 0, 256 / 8));

    k.p = p;
    k.g = g;
    k.pubKey = firendPubKey;
    k.ownPubKey = publicKey;
    k.ownPrvKey = privateKey;
    k.roomId = AuthUtils.userIdToRoomId(userId);
    k.key = secretKey;
    k.userId = userId;
    k.timestamp = new Date().getTime();

    KeyStorageDB.getInstance(context).addKey(k);

    return k;
  }

  private static void doEncrypt(Message msg, Key key) throws Exception {
    msg.text = encryptText(msg.text, key.key.toByteArray());
    msg.key = key.ownPubKey.toString();
  }

  private static String decryptText(String encrypted, byte[] key) throws Exception {
    SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
    Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.DECRYPT_MODE, skeySpec);

    return new String(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT))) + "\nKey=" + new BigInteger(key).toString();
  }

  private static String encryptText(String text, byte[] key) throws Exception {
    SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
    Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.ENCRYPT_MODE, skeySpec);

    byte[] encrypted = cipher.doFinal(text.getBytes());
    return Base64.encodeToString(encrypted, Base64.DEFAULT);
  }

  private static byte[] subArray(byte[] array, @SuppressWarnings("SameParameterValue") int start, int length) {
    int end = start + length;
    if (array.length < end) throw new RuntimeException("No enough elements in array");
    byte[] ret = new byte[length];
    int j = 0;
    for (int i = start; i < end; i++)
      ret[j++] = array[i];

    return ret;
  }
}
