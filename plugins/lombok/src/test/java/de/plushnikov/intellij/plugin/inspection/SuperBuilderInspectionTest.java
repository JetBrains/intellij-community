package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class SuperBuilderInspectionTest extends LombokInspectionTest {

  @Override
  protected String getTestDataPath() {
    return TEST_DATA_INSPECTION_DIRECTORY + "/superbuilder";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testBuilderDefaultValue() {
    doTest();
  }

  public void testBuilderInvalidIdentifier() {
    doTest();
  }

  public void testBuilderRightType() {
    doTest();
  }

  public void testBuilderDefaultsWarnings() {
    doTest();
  }

  public void testBuilderInvalidUse() {
    doTest();
  }

  public void testBuilderInvalidInnerBuilderClass() {
    doTest();
  }

  public void testBuilderObtainVia() {
    doTest();
  }
}
