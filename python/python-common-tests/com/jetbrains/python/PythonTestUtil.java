// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

/**
 * @author yole
 */
public final class PythonTestUtil {
  private PythonTestUtil() {
  }

  public static String getTestDataPath() {
    return PythonHelpersLocator.getPythonCommunityPath() + "/testData";
  }
}
