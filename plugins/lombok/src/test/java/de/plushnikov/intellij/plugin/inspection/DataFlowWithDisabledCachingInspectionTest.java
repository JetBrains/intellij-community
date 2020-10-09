package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.util.RecursionManager;


public class DataFlowWithDisabledCachingInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/diverse";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new DataFlowInspection();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  public void testDefaultBuilderFinalValueInspectionIsAlwaysThat() {
    doTest();
  }
}
