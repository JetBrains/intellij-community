/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diagnostic;

import org.apache.log4j.Level;

public class DefaultLogger extends Logger {
  public DefaultLogger(String category) {
  }

  public boolean isDebugEnabled() {
    return false;
  }

  public void debug(String message) {
  }

  public void debug(Throwable t) {
  }

  public void error(String message, Throwable t, String[] details) {
    System.err.println("ERROR: " + message);
    t.printStackTrace();
    if (details != null && details.length > 0) {
      System.out.println("details: ");
      for (int i = 0; i < details.length; i++) {
        System.out.println(details[i]);
      }
    }

    throw new AssertionError(message);
  }

  public void info(String message) {
  }

  public void info(String message, Throwable t) {
  }

  public void setLevel(Level level) {
  }
}