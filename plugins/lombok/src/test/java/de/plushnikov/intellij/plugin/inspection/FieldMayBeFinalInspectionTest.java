package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.style.FieldMayBeFinalInspection;

public class FieldMayBeFinalInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/canBeFinalInspection";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new FieldMayBeFinalInspection();
  }

  public void testClassNormal() {
    doTest();
  }

  public void testClassWithData() {
    doTest();
  }

  public void testClassWithFieldSetter() {
    doTest();
  }

  public void testClassWithGetter() {
    doTest();
  }

  public void testClassWithSetter() {
    doTest();
  }

  public void testClassWithValue() {
    doTest();
  }

}
