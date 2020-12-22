package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class DefaultConstructorInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/defaultConstructor";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testClassWithData() {
    doTest();
  }

  public void testClassWithJavaConstructor() {
    doTest();
  }

  public void testClassWithLombokConstructor() {
    doTest();
  }

  public void testClassWithLombokDefaultConstructor() {
    doTest();
  }

  public void testDataWithParentClassWithoutDefaultConstructor() {
    doTest();
  }

  public void testDataWithParentClassWithoutDefaultConstructor771() {
    doTest();
  }
}
