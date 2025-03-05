// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class Maven40Sl4LoggerFactory implements ILoggerFactory {

  public Maven40Sl4LoggerFactory() {
  }

  @Override
  public Logger getLogger(String s) {
    return new Maven40Sl4jLoggerWrapper(s);
  }
}
