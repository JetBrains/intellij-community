package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.nullable.NullableStuffInspectionBase;


public class LombokNullableStuffInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/diverse";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new NullableStuffInspectionBase();
  }

  public void testNoRedundantUnderNullMarked() {
    doTest();
  }
}
