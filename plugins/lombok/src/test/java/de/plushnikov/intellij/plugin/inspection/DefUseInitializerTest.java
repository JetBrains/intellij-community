package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.defUse.DefUseInspection;


public class DefUseInitializerTest extends LombokInspectionTest {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/defUse";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new DefUseInspection();
  }

  public void testUnusedInitializerBuilderDefault() {
    doTest();
  }
}
