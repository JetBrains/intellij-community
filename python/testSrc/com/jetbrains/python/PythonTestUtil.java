package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PythonTestUtil {
  private PythonTestUtil() {
  }

  public static String getTestDataPath() {
    return PyTestCase.getPythonCommunityPath() + "/testData";
  }
}
