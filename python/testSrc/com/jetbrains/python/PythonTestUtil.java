package com.jetbrains.python;

/**
 * @author yole
 */
public class PythonTestUtil {
  private PythonTestUtil() {
  }

  public static String getTestDataPath() {
    return PythonHelpersLocator.getPythonCommunityPath() + "/testData";
  }
}
