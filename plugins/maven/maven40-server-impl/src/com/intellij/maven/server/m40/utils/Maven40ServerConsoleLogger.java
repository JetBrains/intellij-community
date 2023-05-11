// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.codehaus.plexus.logging.Logger;
import org.jetbrains.idea.maven.server.MavenRemoteObject;
import org.jetbrains.idea.maven.server.MavenServerConsole;
import org.jetbrains.idea.maven.server.RuntimeRemoteException;

import java.rmi.RemoteException;

public class Maven40ServerConsoleLogger extends MavenRemoteObject implements Logger {
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  private MavenServerConsole myWrappee;
  private int myThreshold;

  void doPrint(int level, String message, Throwable throwable) {
    if (level < myThreshold) return;

    if (!message.endsWith(LINE_SEPARATOR)) {
      message += LINE_SEPARATOR;
    }

    if (myWrappee != null) {
      try {
        myWrappee.printMessage(level, message, wrapException(throwable));
      }
      catch (RemoteException e) {
        throw new RuntimeRemoteException(e);
      }
    }
  }

  public void setWrappee(MavenServerConsole wrappee) {
    myWrappee = wrappee;
  }

  @Override
  public void debug(String string, Throwable throwable) {
    doPrint(MavenServerConsole.LEVEL_DEBUG, string, throwable);
  }

  @Override
  public void info(String string, Throwable throwable) {
    doPrint(MavenServerConsole.LEVEL_INFO, string, throwable);
  }

  @Override
  public void warn(String string, Throwable throwable) {
    doPrint(MavenServerConsole.LEVEL_WARN, string, throwable);
  }

  @Override
  public void error(String string, Throwable throwable) {
    doPrint(MavenServerConsole.LEVEL_ERROR, string, throwable);
  }

  @Override
  public void fatalError(String string, Throwable throwable) {
    doPrint(MavenServerConsole.LEVEL_FATAL, string, throwable);
  }

  @Override
  public void debug(String message) {
    debug(message, null);
  }

  @Override
  public boolean isDebugEnabled() {
    return getThreshold() <= MavenServerConsole.LEVEL_DEBUG;
  }

  @Override
  public void info(String message) {
    info(message, null);
  }

  @Override
  public boolean isInfoEnabled() {
    return getThreshold() <= MavenServerConsole.LEVEL_INFO;
  }

  @Override
  public void warn(String message) {
    warn(message, null);
  }

  @Override
  public boolean isWarnEnabled() {
    return getThreshold() <= MavenServerConsole.LEVEL_WARN;
  }

  @Override
  public void error(String message) {
    error(message, null);
  }

  @Override
  public boolean isErrorEnabled() {
    return getThreshold() <= MavenServerConsole.LEVEL_ERROR;
  }

  @Override
  public void fatalError(String message) {
    fatalError(message, null);
  }

  @Override
  public boolean isFatalErrorEnabled() {
    return getThreshold() <= MavenServerConsole.LEVEL_FATAL;
  }

  @Override
  public void setThreshold(int threshold) {
    this.myThreshold = threshold;
  }

  @Override
  public int getThreshold() {
    return myThreshold;
  }

  @Override
  public Logger getChildLogger(String s) {
    return null;
  }

  @Override
  public String getName() {
    return toString();
  }
}