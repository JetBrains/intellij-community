package org.jetbrains.idea.maven.core;

import com.intellij.openapi.diagnostic.Logger;

public class MavenLog {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven");

  public static void info(String message) {
    //if (ApplicationManager.getApplication().isUnitTestMode()) {
    //  LOG.warn(message);
    //} else {
    LOG.info(message);
    //}
  }

  public static void info(Throwable throwable) {
    //if (ApplicationManager.getApplication().isUnitTestMode()) {
    //  LOG.warn(throwable);
    //} else {
    LOG.info(throwable);
    //}
  }

  public static void warn(Throwable throwable) {
    LOG.warn(throwable);
  }

  public static void error(Throwable throwable) {
    error("", throwable);
  }

  public static void error(String message, Throwable throwable) {
    LOG.error(message, throwable);
  }
}
