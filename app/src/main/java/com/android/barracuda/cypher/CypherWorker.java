package com.android.barracuda.cypher;

import android.content.Context;
import android.util.Base64;
import com.android.barracuda.cypher.exceptions.NoKeyException;
import com.android.barracuda.data.FBaseEntities;
import com.android.barracuda.data.KeyStorageDB;
import com.android.barracuda.data.PublicKeysDB;
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

public class CypherWorker {

  private static final String ALG = "AES";

  public static void decrypt(Message msg, Context context) throws NoKeyException {
    if (msg.key == null) return;

    BigInteger secretKey = KeyStorageDB.getInstance(context).getSecretKeyFor(msg.key);

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

    msg.text = decryptText(msg.text, secretKey.toByteArray());
  }

  public static void encrypt(final Message msg, final Context context) {
    BigInteger secretKey = KeyStorageDB.getInstance(context).getSecretKeyFor(msg.key);
    if (secretKey == null) {
      FirebaseDatabase.getInstance().getReference().child(FBaseEntities.PUBLIC_KEYS + FBaseEntities.DELIM + msg.friendId)
        .orderByChild("timestamp").limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
          PublicKeys pk = dataSnapshot.getValue(PublicKeys.class);
          try {
            Key key = handlePublicKey(msg.friendId, pk, context);
            doEncrypt(msg, key.key);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
          throw new UnsupportedOperationException();
        }
      });
    } else {
      doEncrypt(msg, secretKey);
    }
  }

  private static byte[] subArray(byte[] array, int start, int length) {
    int end = start + length;
    if (array.length < end) throw new RuntimeException("No enough elements in array");
    byte[] ret = new byte[length];
    int j = 0;
    for (int i = start; i < end; i++)
      ret[j++] = array[i];

    return ret;
  }

  private static BigInteger calcSharedSecretKey(BigInteger p, BigInteger privateKey, BigInteger friendPubKey) {
    return friendPubKey.modPow(privateKey, p);
  }

  private static Key handlePublicKey(String userId, PublicKeys keys, Context context) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
    Key k = new Key();
    BigInteger p = new BigInteger(keys.p);

    KeyPairGenerator kpg = KeyPairGenerator.getInstance("DiffieHellman");
    DHParameterSpec param = new DHParameterSpec(p, new BigInteger(keys.g));
    kpg.initialize(param);
    KeyPair dkp = kpg.generateKeyPair();

    BigInteger publicKey = ((javax.crypto.interfaces.DHPublicKey) dkp.getPublic()).getY();
    BigInteger privateKey = ((javax.crypto.interfaces.DHPrivateKey) dkp.getPrivate()).getX();
    BigInteger firendPubKey = new BigInteger(keys.key);
    BigInteger secretKey = new BigInteger(subArray(calcSharedSecretKey(p, privateKey, firendPubKey).toByteArray(), 0, 256 / 8));

    k.p = p;
    k.pubKey = firendPubKey;
    k.ownPubKey = publicKey;
    k.ownPrvKey = privateKey;
    k.roomId = AuthUtils.userIdToRoomId(userId);
    k.key = secretKey;
    k.timestamp = new Date().getTime();

    KeyStorageDB.getInstance(context).addKey(k);

    return k;
  }

  private static void doEncrypt(Message msg, BigInteger secretKey) {
    msg.key = secretKey.toString();
    msg.text = encryptText(msg.text, secretKey.toByteArray());
  }

  private static String decryptText(String encrypted, byte[] key) {
    try {
      SecretKeySpec skeySpec = new SecretKeySpec(key, ALG);
      Cipher cipher = Cipher.getInstance(ALG);
      cipher.init(Cipher.DECRYPT_MODE, skeySpec);

      return new String(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT)));
    } catch (Exception e) {
      return encrypted;
    }
  }

  private static String encryptText(String text, byte[] key) {
    try {
      SecretKeySpec skeySpec = new SecretKeySpec(key, ALG);
      Cipher cipher = Cipher.getInstance(ALG);
      cipher.init(Cipher.ENCRYPT_MODE, skeySpec);

      byte[] encrypted = cipher.doFinal(text.getBytes());
      return Base64.encodeToString(encrypted, Base64.DEFAULT);
    } catch (Exception e) {
      return text;
    }
  }
}
