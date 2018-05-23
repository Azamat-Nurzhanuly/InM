package com.android.barracuda.cypher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Base64;
import com.android.barracuda.cypher.exceptions.NoKeyException;
import com.android.barracuda.cypher.models.Key;
import com.android.barracuda.cypher.models.PublicKeys;
import com.android.barracuda.cypher.models.PublicKeysToFb;
import com.android.barracuda.data.FBaseEntities;
import com.android.barracuda.data.KeyStorageDB;
import com.android.barracuda.data.PublicKeysDB;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.Message;
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

public class PrivateChatCypherWorker implements CypherWorker {
  private String roomId;
  private String friendId;
  private Context context;
  private Key lastKey = null;

  public PrivateChatCypherWorker(String roomId, String friendId, Context context) {
    this.roomId = roomId;
    this.friendId = friendId;
    this.context = context;
  }

  @Override
  public void encryptAndSend(final Message msg) {
    FirebaseDatabase.getInstance().getReference().child(FBaseEntities.PUBLIC_KEYS + "/" + msg.friendId + "/1").addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        PublicKeysToFb pks = dataSnapshot.getValue(PublicKeysToFb.class);

        if (pks == null || pks.key == null) {
          sendMessage(msg);
          return;
        }

        long recKeyTs = pks.timestamp;
        Key key = checkAndGetLastKey(pks.timestamp, msg.idReceiver);

        if (key == null) {
          key = KeyStorageDB.getInstance(context).getKey(pks.timestamp, msg.idReceiver);
        }

        if (key == null) {
          PublicKeys ownPubKeys = PublicKeysDB.getInstance(context).getLast();
          if (ownPubKeys == null) {
            sendMessage(msg);
            return;
          }

          key = checkAndGetLastKey(ownPubKeys.timestamp, msg.idReceiver);

          if (key == null) {
            key = KeyStorageDB.getInstance(context).getKey(ownPubKeys.timestamp, msg.idReceiver);
          }

          if (key != null) recKeyTs = ownPubKeys.timestamp;
        }

        try {
          if (key == null) {
            key = handlePublicKey(msg, pks);
          } else {
            msg.recKeyTs = recKeyTs;
            msg.key = key.ownPubKey.toString();
          }
          doEncrypt(msg, key);
          sendMessage(msg);
        } catch (Exception e) {
          e.printStackTrace();
        }

        setLastKey(key);
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {}
    });
  }

  @Override
  public void decrypt(Message msg) throws NoKeyException {
    if (msg.key == null) return;
    Key key = checkAndGetLastKey(msg.recKeyTs, msg.idReceiver);
    BigInteger secretKey = key == null ? KeyStorageDB.getInstance(context).getSecretKey(msg.recKeyTs, msg.idReceiver) : key.key;

    if (!msg.idSender.equals(StaticConfig.UID)) {

      if (secretKey == null) {
        BigInteger friendPublicKey = new BigInteger(msg.key);

        PublicKeys ownPubKeys = PublicKeysDB.getInstance(context).getKeyByTimestamp(msg.recKeyTs);

        secretKey = new BigInteger(subArray(calcSharedSecretKey(ownPubKeys.p, ownPubKeys.prvKey, friendPublicKey).toByteArray(), 0, 256 / 8));

        setLastKey(addKey(msg.recKeyTs, msg.idReceiver, msg.friendId, friendPublicKey, ownPubKeys.pubKey, secretKey, System.currentTimeMillis()));
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

  private Key checkAndGetLastKey(long pksTimestamp, String roomId) {
    if (lastKey != null && lastKey.friendKeyTs == pksTimestamp && lastKey.roomId.equals(roomId)) {
      return lastKey.copy();
    }
    return null;
  }

  private void setLastKey(Key key) {
    synchronized (this) {
      lastKey = key;
    }
  }

  private void sendMessage(Message msg) {
    FirebaseDatabase.getInstance().getReference().child("message/" + msg.idReceiver).push().setValue(msg);
  }

  private BigInteger calcSharedSecretKey(BigInteger p, BigInteger privateKey, BigInteger friendPubKey) {
    return friendPubKey.modPow(privateKey, p);
  }

  private Key handlePublicKey(Message msg, PublicKeysToFb friendKeys) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
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

  private Key addKey(long friendKeyTs, String roomId, String friendId, BigInteger pubKey, BigInteger ownPubKey, BigInteger key, long timestamp) {
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

  private void doEncrypt(Message msg, Key key) throws Exception {
    msg.text = encryptText(msg.text, key.key.toByteArray());
  }

  private String decryptText(String encrypted, byte[] key) throws Exception {
    SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
    @SuppressLint("GetInstance") Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.DECRYPT_MODE, skeySpec);

    return new String(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT))) + "\nKey=" + new BigInteger(key).toString();
  }

  private String encryptText(String text, byte[] key) throws Exception {
    SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
    @SuppressLint("GetInstance") Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.ENCRYPT_MODE, skeySpec);

    byte[] encrypted = cipher.doFinal(text.getBytes());
    return Base64.encodeToString(encrypted, Base64.DEFAULT);
  }

  private byte[] subArray(byte[] array, @SuppressWarnings("SameParameterValue") int start, int length) {
    int end = start + length;
    if (array.length < end) throw new RuntimeException("No enough elements in array");
    byte[] ret = new byte[length];
    int j = 0;
    for (int i = start; i < end; i++)
      ret[j++] = array[i];

    return ret;
  }
}
