package com.intellij.spellchecker.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;

public abstract class SpellcheckerInspectionTestCase extends JavaCodeInsightFixtureTestCase {

  @NonNls
  protected String DATA_PATH = FileUtil.toSystemIndependentName(PathManager.getHomePath()) + "/plugins/spellchecker/core/tests/testData";

  protected void doTest(String file, LocalInspectionTool... tools) throws Throwable {
    myFixture.enableInspections(tools);
    myFixture.testHighlighting(false, false, true, file);
  }

}
