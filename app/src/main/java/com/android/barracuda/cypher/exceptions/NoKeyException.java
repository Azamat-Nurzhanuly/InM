package com.android.barracuda.cypher.exceptions;

/**
 * Created by Khamit Mateyev on 5/17/18.
 */
public class NoKeyException extends Exception {
  public NoKeyException(String message) {
    super(message);
  }

  public NoKeyException() {
  }
}
