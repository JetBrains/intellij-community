package com.jetbrains.python.packaging;

import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyExternalProcessException extends Exception {
  private final int myRetcode;

  public PyExternalProcessException(int retcode, @NotNull String message) {
    super(message);
    myRetcode = retcode;
  }

  public int getRetcode() {
    return myRetcode;
  }
}
