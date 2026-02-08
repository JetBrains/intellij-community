package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class RedundantSlf4jDefinitionInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/redundantSlf4jDeclaration";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantSlf4jDefinitionInspection();
  }

  public void testRedundantSlf4jDefinition() {
    doTest();
    checkQuickFix("Replace logger field with @Slf4j annotation");
  }

}
