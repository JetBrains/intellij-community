package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;

public class DataFlowInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/diverse";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new DataFlowInspection();
  }

  public void testIssue440() {
    doTest();
  }

  public void testExtensionMethodDfa() {
    doTest();
  }

}
