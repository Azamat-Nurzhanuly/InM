package com.android.barracuda.cypher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Base64;
import android.util.Log;
import com.android.barracuda.cypher.callback.MessageActivityCallback;
import com.android.barracuda.cypher.models.DHKeys;
import com.android.barracuda.cypher.models.Key;
import com.android.barracuda.cypher.models.PublicKeysFb;
import com.android.barracuda.data.KeyStorageDB;
import com.android.barracuda.data.PublicKeysDB;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.Message;
import com.google.firebase.database.FirebaseDatabase;

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

  public void decrypt(Message msg, MessageActivityCallback afterDecrypted) {
    if (msg.key == null) return;
    Key key = checkAndGetLastKey(msg.recKeyTs);
    BigInteger secretKey = key == null ? KeyStorageDB.getInstance(context).getSecretKey(msg.recKeyTs, msg.idReceiver) : key.key;

    if (!msg.idSender.equals(StaticConfig.UID)) {

      if (secretKey == null) {
        BigInteger friendPublicKey = new BigInteger(msg.key);

        DHKeys ownPubKeys = PublicKeysDB.getInstance(context).getKeyByTimestamp(msg.recKeyTs);

        secretKey = new BigInteger(subArray(calcSharedSecretKey(ownPubKeys.p, ownPubKeys.prvKey, friendPublicKey).toByteArray(), 0, 32));

        setLastKey(addKey(msg.recKeyTs, msg.idReceiver, msg.friendId, friendPublicKey, ownPubKeys.pubKey, secretKey, System.currentTimeMillis()));
      }
    }

    if (secretKey == null) {
      msg.text = "Could not decrypt. Cause: no key\n" + msg.text;
      Log.e("CypherWorker", "No key");
    } else {
      try {
        msg.text = decryptText(msg.text, secretKey.toByteArray()) + "\nEncrypted: " + msg.text;
      } catch (Exception e) {
        msg.text = "Could not decrypt. Cause: no key\n" + e.getMessage();
        Log.e("CypherWorker", "Error", e);
      }
    }

    afterDecrypted.processMessage(msg);
  }

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

  protected void sendMessageTo(Message msg, String entity) {
    FirebaseDatabase.getInstance().getReference().child(entity).push().setValue(msg);
  }

  protected BigInteger calcSharedSecretKey(BigInteger p, BigInteger privateKey, BigInteger friendPubKey) {
    return friendPubKey.modPow(privateKey, p);
  }

  protected Key handlePublicKey(Message msg, PublicKeysFb friendKeys) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
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

    msg.key = publicKey.toString();
    msg.recKeyTs = friendKeys.timestamp;

    return addKey(friendKeys.timestamp, msg.idReceiver, msg.friendId, friendPubKey, publicKey, secretKey, System.currentTimeMillis());
  }

  protected Key addKey(long friendKeyTs, String roomId, String friendId, BigInteger pubKey, BigInteger ownPubKey, BigInteger key, long timestamp) {
    Key k = new Key();
    k.friendKeyTs = friendKeyTs;
    k.roomId = roomId;
    k.friendId = friendId;
    k.pubKey = pubKey;
    k.ownPubKey = ownPubKey;
    k.key = key;
    k.timestamp = timestamp;

    KeyStorageDB.getInstance(context).addKey(k);

    return k;
  }

  protected void doEncrypt(Message msg, Key key) throws Exception {
    msg.text = encryptText(msg.text, key.key.toByteArray());
    msg.recKeyTs = key.timestamp;
  }

  protected String decryptText(String encrypted, byte[] key) throws Exception {
    SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
    @SuppressLint("GetInstance") Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.DECRYPT_MODE, skeySpec);

    return new String(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT))) + "\nKey=" + new BigInteger(key).toString();
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
