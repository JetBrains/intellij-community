package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class CleanupInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/cleanup";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testCleanupExample() {
    doTest();
  }

  public void testCleanupEmptyValue() {
    doTest();
  }

  public void testCleanupWithoutInitializer() {
    doTest();
  }

  public void testCleanupWrongElement() {
    doTest();
  }

  public void testCleanupWrongValue() {
    doTest();
  }

}
