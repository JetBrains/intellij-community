/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.testFramework;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TestLogger extends com.intellij.openapi.diagnostic.Logger {
  private org.apache.log4j.Logger myLogger;

  public TestLogger(Logger logger) {
    myLogger = logger;
  }

  public boolean isDebugEnabled() {
    return false;
  }

  public void debug(String message) {
    myLogger.debug(message);
  }

  public void debug(Throwable t) {
    myLogger.debug(t);
  }

  public void error(String message, Throwable t, String[] details) {
    LoggedErrorProcessor.getInstance().processError(message, t, details, myLogger);
  }

  public void info(String message) {
    myLogger.info(message);
  }

  public void info(String message, Throwable t) {
    myLogger.info(message, t);
  }

  public void setLevel(Level level) {
    myLogger.setLevel(level);
  }
}