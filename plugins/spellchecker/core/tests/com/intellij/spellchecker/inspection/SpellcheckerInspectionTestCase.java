package com.intellij.spellchecker.inspection;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.InspectionTestCase;
import org.jetbrains.annotations.NonNls;

public abstract class SpellcheckerInspectionTestCase extends InspectionTestCase {

  @NonNls
  private String DATA_PATH =
    FileUtil.toSystemIndependentName(PathManager.getHomePath()) + "/plugins/spellchecker/core/tests/testData" + getDataPath();

  public String getDataPath() {
    return "";
  }

  @NonNls
  protected String getTestDataPath() {
    return DATA_PATH;
  }
}
