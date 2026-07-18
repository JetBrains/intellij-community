package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class UnusedLoggerInspectionTest extends LombokInspectionTest {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/unusedLogger";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnusedLoggerInspection();
  }

  public void testUnusedLogger() {
    doTest();
    checkQuickFixAll();
  }
}
