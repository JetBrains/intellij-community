package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import de.plushnikov.intellij.plugin.LombokTestUtil;

public class RedundantSlf4jDefinitionInspectionTest extends LombokInspectionTest {

  @Override
  protected String getTestDataPath() {
    return TEST_DATA_INSPECTION_DIRECTORY + "/redundantSlf4jDeclaration";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    LombokTestUtil.loadSlf4jLibrary(myFixture.getProjectDisposable(), getModule());
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantSlf4jDefinitionInspection();
  }

  public void testRedundantSlf4jDefinition() {
    doTest();
    checkQuickFix("Annotate class 'RedundantSlf4jDefinition' as @Slf4j");
  }

}
