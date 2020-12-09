// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;

public final class MavenLog {
  public static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven");

  public static void printInTests(Throwable e) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      e.printStackTrace();
    }
  }
}
