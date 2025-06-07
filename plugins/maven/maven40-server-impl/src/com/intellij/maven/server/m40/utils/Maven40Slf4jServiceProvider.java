// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.slf4j.MavenServiceProvider;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * adapted from {@link MavenServiceProvider}
 */
public class Maven40Slf4jServiceProvider implements SLF4JServiceProvider {

  /**
   * Declare the version of the SLF4J API this implementation is compiled against.
   * The value of this field is modified with each major release.
   */
  // to avoid constant folding by the compiler, this field must *not* be final
  @SuppressWarnings({"checkstyle:StaticVariableName", "checkstyle:VisibilityModifier"})
  public static String REQUESTED_API_VERSION = "2.0.99"; // !final

  private ILoggerFactory loggerFactory = new Maven40Sl4LoggerFactory();
  private IMarkerFactory markerFactory = new BasicMarkerFactory();
  private MDCAdapter mdcAdapter = new BasicMDCAdapter();

  @Override
  public ILoggerFactory getLoggerFactory() {
    return loggerFactory;
  }

  @Override
  public IMarkerFactory getMarkerFactory() {
    return markerFactory;
  }

  @Override
  public MDCAdapter getMDCAdapter() {
    return mdcAdapter;
  }

  @Override
  public String getRequestedApiVersion() {
    return REQUESTED_API_VERSION;
  }

  @Override
  public void initialize() {
    // already initialized
  }
}
