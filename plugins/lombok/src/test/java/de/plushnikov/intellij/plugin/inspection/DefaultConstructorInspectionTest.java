package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class DefaultConstructorInspectionTest extends LombokInspectionTest {

  @Override
  protected String getTestDataPath() {
    return TEST_DATA_INSPECTION_DIRECTORY + "/defaultConstructor";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testClassWithData() throws Exception {
    doTest();
  }

  public void testClassWithJavaConstructor() throws Exception {
    doTest();
  }

  public void testClassWithLombokConstructor() throws Exception {
    doTest();
  }

  public void testClassWithLombokDefaultConstructor() throws Exception {
    doTest();
  }

  public void testDataWithParentClassWithoutDefaultConstructor() throws Exception {
    doTest();
  }
}
