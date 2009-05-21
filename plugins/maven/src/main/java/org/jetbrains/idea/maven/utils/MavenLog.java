package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.application.ApplicationManager;

public class MavenLog {
  public static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven");

  public static void printInTests(Throwable e) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      e.printStackTrace();
    }
  }
}
