package com.jetbrains.python;

import com.intellij.openapi.application.PathManager;

/**
 * @author yole
 */
public class PythonTestUtil {
  private PythonTestUtil() {
  }

  public static String getTestDataPath() {
    return PathManager.getHomePath() + "/python/testData";
  }
}
