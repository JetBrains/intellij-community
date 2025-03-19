// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.api.cli.Logger;
import org.jetbrains.idea.maven.server.MavenRemoteObject;
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicator;
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicatorImpl;
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicatorWrapper;

public class Maven40ServerConsoleLogger extends MavenRemoteObject implements Logger, MavenServerConsoleIndicatorWrapper {
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  private MavenServerConsoleIndicatorImpl myWrappee;
  private int myThreshold;

  void doPrint(int level, String message, Throwable throwable) {
    if (level < myThreshold) return;

    if (!message.endsWith(LINE_SEPARATOR)) {
      message += LINE_SEPARATOR;
    }

    if (myWrappee != null) {
      myWrappee.printMessage(level, message, wrapException(throwable));
    }
  }

  @Override
  public void setWrappee(MavenServerConsoleIndicatorImpl wrappee) {
    myWrappee = wrappee;
  }

  @Override
  public void debug(String string, Throwable throwable) {
    doPrint(MavenServerConsoleIndicator.LEVEL_DEBUG, string, throwable);
  }

  @Override
  public void info(String string, Throwable throwable) {
    doPrint(MavenServerConsoleIndicator.LEVEL_INFO, string, throwable);
  }

  @Override
  public void warn(String string, Throwable throwable) {
    doPrint(MavenServerConsoleIndicator.LEVEL_WARN, string, throwable);
  }

  @Override
  public void error(String string, Throwable throwable) {
    doPrint(MavenServerConsoleIndicator.LEVEL_ERROR, string, throwable);
  }

  public void fatalError(String string, Throwable throwable) {
    doPrint(MavenServerConsoleIndicator.LEVEL_FATAL, string, throwable);
  }

  @Override
  public void log(Level level, String message, Throwable error) {
    switch (level) {
      case DEBUG:
        debug(message, error);
        break;
      case INFO:
        info(message, error);
        break;
      case WARN:
        warn(message, error);
        break;
      case ERROR:
        error(message, error);
        break;
    }
  }

  @Override
  public void debug(String message) {
    debug(message, null);
  }

  public boolean isDebugEnabled() {
    return getThreshold() <= MavenServerConsoleIndicator.LEVEL_DEBUG;
  }

  @Override
  public void info(String message) {
    info(message, null);
  }

  public boolean isInfoEnabled() {
    return getThreshold() <= MavenServerConsoleIndicator.LEVEL_INFO;
  }

  @Override
  public void warn(String message) {
    warn(message, null);
  }

  public boolean isWarnEnabled() {
    return getThreshold() <= MavenServerConsoleIndicator.LEVEL_WARN;
  }

  @Override
  public void error(String message) {
    error(message, null);
  }

  public boolean isErrorEnabled() {
    return getThreshold() <= MavenServerConsoleIndicator.LEVEL_ERROR;
  }

  public void fatalError(String message) {
    fatalError(message, null);
  }

  public boolean isFatalErrorEnabled() {
    return getThreshold() <= MavenServerConsoleIndicator.LEVEL_FATAL;
  }

  public void setThreshold(int threshold) {
    this.myThreshold = threshold;
  }

  public int getThreshold() {
    return myThreshold;
  }

  public Logger getChildLogger(String s) {
    return null;
  }

  public String getName() {
    return toString();
  }
}