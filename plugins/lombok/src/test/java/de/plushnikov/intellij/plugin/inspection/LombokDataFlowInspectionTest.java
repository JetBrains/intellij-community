package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;


public class LombokDataFlowInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/diverse";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ConstantValueInspection();
  }

  public void testDefaultBuilderFinalValueInspectionIsAlwaysThat() {
    doTest();
  }

  public void testAccessorContract() {
    doTest();
  }
}
