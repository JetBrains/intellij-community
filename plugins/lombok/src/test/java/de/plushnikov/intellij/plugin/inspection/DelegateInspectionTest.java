package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class DelegateInspectionTest extends LombokInspectionTest {

  @Override
  protected String getTestDataPath() {
    return TEST_DATA_INSPECTION_DIRECTORY + "/delegate";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testConcreteType() throws Exception {
    doTest();
  }

  public void testOnMethodWithParameter() throws Exception {
    doTest();
  }

  public void testOnStaticFieldOrMethod() throws Exception {
    doTest();
  }

  public void testRecursionType() throws Exception {
    doTest();
  }

}
