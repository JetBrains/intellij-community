package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class BuilderInspectionTest extends LombokInspectionTest {

  @Override
  protected String getTestDataPath() {
    return TEST_DATA_INSPECTION_DIRECTORY + "/builder";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testBuilderInvalidIdentifier() throws Exception {
    doTest();
  }

  public void testBuilderRightType() throws Exception {
    doTest();
  }

  public void testBuilderInvalidUse() throws Exception {
    doTest();
  }

  public void testBuilderObtainVia() throws Exception {
    doTest();
  }

  public void testBuilderDefaultsWarnings() throws Exception {
    //TODO implement test after adding support for Builder.Default
    doTest();
  }
}
