package com.intellij.testFramework;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public abstract class InspectionFixtureTestCase extends CodeInsightFixtureTestCase {
  public void doTest(@NonNls String folderName, LocalInspectionTool tool) throws Exception {
    doTest(folderName, new LocalInspectionToolWrapper(tool));
  }

  public void doTest(@NonNls String folderName, InspectionTool tool) throws Exception {
    myFixture.testInspection(folderName, tool);
  }
}
