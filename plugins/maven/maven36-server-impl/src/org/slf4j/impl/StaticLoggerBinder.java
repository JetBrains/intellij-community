// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.slf4j.impl;


import org.jetbrains.idea.maven.server.Maven3WrapperSl4LoggerFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

public final class StaticLoggerBinder implements LoggerFactoryBinder {
  public static final String REQUESTED_API_VERSION = "1.7.25";
  private static final String LOGGER_FACTORY_CLASS_STR = Maven3WrapperSl4LoggerFactory.class.getName();
  private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();
  private final ILoggerFactory wrapperLoggerFactory = new Maven3WrapperSl4LoggerFactory();

  private StaticLoggerBinder() {
  }

  public static StaticLoggerBinder getSingleton() {
    return SINGLETON;
  }

  @Override
  public ILoggerFactory getLoggerFactory() {
    return this.wrapperLoggerFactory;
  }

  @Override
  public String getLoggerFactoryClassStr() {
    return LOGGER_FACTORY_CLASS_STR;
  }
}
