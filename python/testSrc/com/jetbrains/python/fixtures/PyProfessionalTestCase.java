package com.jetbrains.python.fixtures;

import com.intellij.openapi.application.PathManager;

/**
 * @author yole
 */
public abstract class PyProfessionalTestCase extends PyTestCase {
  @Override
  protected String getTestDataPath() {
    return getProfessionalTestDataPath();
  }

  public static String getProfessionalTestDataPath() {
    return PathManager.getHomePath() + "/python/testData";
  }
}
