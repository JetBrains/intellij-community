/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diagnostic;

import org.apache.log4j.Level;

public abstract class Logger {
  public interface Factory {
    Logger getLoggerInstance(String category);
  }

  public static Factory ourFactory = new Factory() {
    public Logger getLoggerInstance(String category) {
      return new DefaultLogger(category);
    }
  };

  public static void setFactory(Factory factory) {
    ourFactory = factory;
  }

  public static Logger getInstance(String category) {
    return ourFactory.getLoggerInstance(category);
  }

  public abstract boolean isDebugEnabled();

  public abstract void debug(String message);
  public abstract void debug(Throwable t);

  public void error(String message) {
    error(message, new Throwable(), new String[0]);
  }

  public void error(String message, String[] details) {
    error(message, new Throwable(), details);
  }

  public void error(String message, Throwable e) {
    error(message, e, new String[0]);
  }

  public void error(Throwable t) {
    error("", t, new String[0]);
  }

  public abstract void error(String message, Throwable t, String[] details);

  public abstract void info(String message);

  public abstract void info(String message, Throwable t);

  public void info(Throwable t) {
    info("", t);
  }

  public boolean assertTrue(boolean value, String message) {
    if (!value) {
      String resultMessage = "Assertion failed";
      if (!message.equals("")) resultMessage += ": " + message;

      error(resultMessage, new Throwable());
    }

    return value;
  }

  public boolean assertTrue(boolean value) {
    if (!value) {
      return assertTrue(value, "");
    }
    else {
      return true;
    }
  }

  public abstract void setLevel(Level level);

}