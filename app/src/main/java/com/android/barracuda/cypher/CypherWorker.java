package com.android.barracuda.cypher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Base64;
import com.android.barracuda.cypher.callback.MessageActivityCallback;
import com.android.barracuda.cypher.models.Key;
import com.android.barracuda.cypher.models.PublicKeysFb;
import com.android.barracuda.data.KeyStorageDB;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.Message;

import javax.crypto.Cipher;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

public abstract class CypherWorker {
  protected String roomId;
  protected Context context;
  protected Key lastKey = null;

  public CypherWorker(String roomId, Context context) {
    this.roomId = roomId;
    this.context = context;
  }

  public abstract void encrypt(final Message msg, final MessageActivityCallback afterEncrypted) throws Exception;

  public abstract void decrypt(Message msg, MessageActivityCallback afterDecrypted);

  protected Key checkAndGetLastKey(long pksTimestamp) {
    if (lastKey != null && lastKey.friendKeyTs == pksTimestamp && lastKey.roomId.equals(roomId)) {
      return lastKey.copy();
    }
    return null;
  }

  protected void setLastKey(Key key) {
    synchronized (this) {
      lastKey = key;
    }
  }

  protected BigInteger calcSharedSecretKey(BigInteger p, BigInteger privateKey, BigInteger friendPubKey) {
    return friendPubKey.modPow(privateKey, p);
  }

  protected Key handlePublicKey(String roomId, String friendId, PublicKeysFb friendKeys) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
    BigInteger p = new BigInteger(friendKeys.p);
    BigInteger g = new BigInteger(friendKeys.g);
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("DiffieHellman");
    DHParameterSpec param = new DHParameterSpec(p, g);
    kpg.initialize(param);
    KeyPair dkp = kpg.generateKeyPair();

    BigInteger publicKey = ((javax.crypto.interfaces.DHPublicKey) dkp.getPublic()).getY();
    BigInteger privateKey = ((javax.crypto.interfaces.DHPrivateKey) dkp.getPrivate()).getX();
    BigInteger friendPubKey = new BigInteger(friendKeys.key);
    BigInteger secretKey = new BigInteger(subArray(calcSharedSecretKey(p, privateKey, friendPubKey).toByteArray(), 0, 256 / 8));

    return newKey(friendKeys.timestamp, roomId, friendId, friendPubKey, publicKey, secretKey, System.currentTimeMillis());
  }

  protected Key handleAndAddKey(String roomId, String friendId, PublicKeysFb friendKeys) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
    Key key = handlePublicKey(roomId, friendId, friendKeys);
    addKey(key);
    return key;
  }

  private Key newKey(long friendKeyTs, String roomId, String friendId, BigInteger pubKey, BigInteger ownPubKey, BigInteger key, long timestamp) {
    Key k = new Key();
    k.friendKeyTs = friendKeyTs;
    k.roomId = roomId;
    k.friendId = friendId;
    k.pubKey = pubKey;
    k.ownPubKey = ownPubKey;
    k.key = key;
    k.timestamp = timestamp;

    return k;
  }

  protected Key addKey(long friendKeyTs, String roomId, String friendId, BigInteger pubKey, BigInteger ownPubKey, BigInteger key, long timestamp) {
    return addKey(newKey(friendKeyTs, roomId, friendId, pubKey, ownPubKey, key, timestamp));
  }

  protected Key addKey(Key key) {
    KeyStorageDB.getInstance(context).addKey(key);
    return key;
  }

  protected String decryptText(String encrypted, byte[] key) throws Exception {
    SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
    @SuppressLint("GetInstance") Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.DECRYPT_MODE, skeySpec);
    String decrypted = new String(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT)));
    return decrypted
      + ((StaticConfig.TEST_MODE) ? ("\nKey=" + new BigInteger(key).toString() + "\nEncrypted: " + encrypted) : "");
  }

  protected String encryptText(String text, byte[] key) throws Exception {
    SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
    @SuppressLint("GetInstance") Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.ENCRYPT_MODE, skeySpec);

    byte[] encrypted = cipher.doFinal(text.getBytes());
    return Base64.encodeToString(encrypted, Base64.DEFAULT);
  }

  protected byte[] subArray(byte[] array, @SuppressWarnings("SameParameterValue") int start, int length) {
    int end = start + length;
    if (array.length < end) throw new RuntimeException("No enough elements in array");
    byte[] ret = new byte[length];
    int j = 0;
    for (int i = start; i < end; i++)
      ret[j++] = array[i];

    return ret;
  }
}
