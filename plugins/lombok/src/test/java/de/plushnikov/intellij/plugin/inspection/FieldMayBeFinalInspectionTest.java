package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.style.FieldMayBeFinalInspection;

public class FieldMayBeFinalInspectionTest extends LombokInspectionTest {

  @Override
  protected String getTestDataPath() {
    return TEST_DATA_INSPECTION_DIRECTORY + "/canBeFinalInspection";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new FieldMayBeFinalInspection();
  }

  public void testClassNormal() throws Exception {
    doTest();
  }

  public void testClassWithData() throws Exception {
    doTest();
  }

  public void testClassWithFieldSetter() throws Exception {
    doTest();
  }

  public void testClassWithGetter() throws Exception {
    doTest();
  }

  public void testClassWithSetter() throws Exception {
    doTest();
  }

  public void testClassWithValue() throws Exception {
    doTest();
  }

}
