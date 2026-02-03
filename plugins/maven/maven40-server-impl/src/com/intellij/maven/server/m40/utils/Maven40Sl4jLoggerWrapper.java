// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.slf4j.Logger;
import org.slf4j.Marker;

public class Maven40Sl4jLoggerWrapper implements Logger {
  private final String myName;
  private static volatile Maven40ServerConsoleLogger currentWrapper;

  public Maven40Sl4jLoggerWrapper(String name) {
    myName = name;
  }

  public static void setCurrentWrapper(Maven40ServerConsoleLogger currentWrapper) {
    Maven40Sl4jLoggerWrapper.currentWrapper = currentWrapper;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isTraceEnabled() {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    return wrapper != null && wrapper.isDebugEnabled();
  }

  @Override
  public void trace(String s) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null) {
      wrapper.debug(s);
    }
  }

  @Override
  public void trace(String s, Object o) {
    this.debug(s, o);
  }

  @Override
  public void trace(String s, Object o, Object o1) {
    this.debug(s, o, o1);
  }

  @Override
  public void trace(String s, Object... objects) {
    this.debug(s, objects);
  }

  @Override
  public void trace(String s, Throwable throwable) {
    this.debug(s, throwable);
  }

  @Override
  public boolean isTraceEnabled(Marker marker) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    return wrapper != null && wrapper.isDebugEnabled();
  }

  @Override
  public void trace(Marker marker, String s) {
    this.debug(s);
  }

  @Override
  public void trace(Marker marker, String s, Object o) {
    this.debug(s, o);
  }

  @Override
  public void trace(Marker marker, String s, Object o, Object o1) {
    this.debug(s, o, o1);
  }

  @Override
  public void trace(Marker marker, String s, Object... objects) {
    this.debug(s, objects);
  }

  @Override
  public void trace(Marker marker, String s, Throwable throwable) {
    this.debug(s, throwable);
  }

  @Override
  public boolean isDebugEnabled() {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    return wrapper != null && wrapper.isDebugEnabled();
  }

  @Override
  public void debug(String s) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null) {
      wrapper.debug(s);
    }
  }

  @Override
  public void debug(String s, Object o) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null && wrapper.isDebugEnabled()) {
      wrapper.debug(String.format(s, o));
    }
  }

  @Override
  public void debug(String s, Object o, Object o1) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null && wrapper.isDebugEnabled()) {
      wrapper.debug(String.format(s, o, o1));
    }
  }

  @Override
  public void debug(String s, Object... objects) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null && wrapper.isDebugEnabled()) {
      wrapper.debug(String.format(s, objects));
    }
  }

  @Override
  public void debug(String s, Throwable throwable) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null) {
      wrapper.debug(s, throwable);
    }
  }

  @Override
  public boolean isDebugEnabled(Marker marker) {
    return this.isDebugEnabled();
  }

  @Override
  public void debug(Marker marker, String s) {
    this.debug(s);
  }

  @Override
  public void debug(Marker marker, String s, Object o) {
    this.debug(s, o);
  }

  @Override
  public void debug(Marker marker, String s, Object o, Object o1) {
    this.debug(s, o, o1);
  }

  @Override
  public void debug(Marker marker, String s, Object... objects) {
    this.debug(s, objects);
  }

  @Override
  public void debug(Marker marker, String s, Throwable throwable) {
    this.debug(s, throwable);
  }

  @Override
  public boolean isInfoEnabled() {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    return wrapper != null && wrapper.isInfoEnabled();
  }

  @Override
  public void info(String s) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null) {
      wrapper.info(s);
    }
  }

  @Override
  public void info(String s, Object o) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null && wrapper.isInfoEnabled()) {
      wrapper.info(String.format(s, o));
    }
  }

  @Override
  public void info(String s, Object o, Object o1) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null && wrapper.isInfoEnabled()) {
      wrapper.info(String.format(s, o, o1));
    }
  }

  @Override
  public void info(String s, Object... objects) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null && wrapper.isInfoEnabled()) {
      wrapper.info(String.format(s, objects));
    }
  }

  @Override
  public void info(String s, Throwable throwable) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null) {
      wrapper.info(s, throwable);
    }
  }

  @Override
  public boolean isInfoEnabled(Marker marker) {
    return this.isInfoEnabled();
  }

  @Override
  public void info(Marker marker, String s) {
    this.info(s);
  }

  @Override
  public void info(Marker marker, String s, Object o) {
    this.info(s, o);
  }

  @Override
  public void info(Marker marker, String s, Object o, Object o1) {
    this.info(s, o, o1);
  }

  @Override
  public void info(Marker marker, String s, Object... objects) {
    this.info(s, objects);
  }

  @Override
  public void info(Marker marker, String s, Throwable throwable) {
    this.info(s, throwable);
  }

  @Override
  public boolean isWarnEnabled() {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    return wrapper != null && wrapper.isWarnEnabled();
  }

  @Override
  public void warn(String s) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null) {
      wrapper.warn(s);
    }
  }

  @Override
  public void warn(String s, Object o) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null && wrapper.isWarnEnabled()) {
      wrapper.warn(String.format(s, o));
    }
  }

  @Override
  public void warn(String s, Object o, Object o1) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null && wrapper.isWarnEnabled()) {
      wrapper.warn(String.format(s, o, o1));
    }
  }

  @Override
  public void warn(String s, Object... objects) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null && wrapper.isWarnEnabled()) {
      wrapper.warn(String.format(s, objects));
    }
  }

  @Override
  public void warn(String s, Throwable throwable) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null) {
      wrapper.warn(s, throwable);
    }
  }

  @Override
  public boolean isWarnEnabled(Marker marker) {
    return this.isWarnEnabled();
  }

  @Override
  public void warn(Marker marker, String s) {
    this.warn(s);
  }

  @Override
  public void warn(Marker marker, String s, Object o) {
    this.warn(s, o);
  }

  @Override
  public void warn(Marker marker, String s, Object o, Object o1) {
    this.warn(s, o, o1);
  }

  @Override
  public void warn(Marker marker, String s, Object... objects) {
    this.warn(s, objects);
  }

  @Override
  public void warn(Marker marker, String s, Throwable throwable) {
    this.warn(s, throwable);
  }

  @Override
  public boolean isErrorEnabled() {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    return wrapper != null && wrapper.isErrorEnabled();
  }

  @Override
  public void error(String s) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null) {
      wrapper.error(s);
    }
  }

  @Override
  public void error(String s, Object o) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null && wrapper.isErrorEnabled()) {
      wrapper.error(String.format(s, o));
    }
  }

  @Override
  public void error(String s, Object o, Object o1) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null && wrapper.isErrorEnabled()) {
      wrapper.error(String.format(s, o, o1));
    }
  }

  @Override
  public void error(String s, Object... objects) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null && wrapper.isErrorEnabled()) {
      wrapper.error(String.format(s, objects));
    }
  }

  @Override
  public void error(String s, Throwable throwable) {
    Maven40ServerConsoleLogger wrapper = currentWrapper;
    if (wrapper != null) {
      wrapper.error(s, throwable);
    }
  }

  @Override
  public boolean isErrorEnabled(Marker marker) {
    return this.isErrorEnabled();
  }

  @Override
  public void error(Marker marker, String s) {
    this.error(s);
  }

  @Override
  public void error(Marker marker, String s, Object o) {
    this.error(s, o);
  }

  @Override
  public void error(Marker marker, String s, Object o, Object o1) {
    this.error(s, o, o1);
  }

  @Override
  public void error(Marker marker, String s, Object... objects) {
    this.error(s, objects);
  }

  @Override
  public void error(Marker marker, String s, Throwable throwable) {
    this.error(s, throwable);
  }
}