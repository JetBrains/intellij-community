package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class OnXAnnotationInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/onXAnnotation";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testConstructorOnConstructor() {
    doTest();
  }

  public void testGetterOnMethod() {
    doTest();
  }

  public void testSetterOnParam() {
    doTest();
  }

}
