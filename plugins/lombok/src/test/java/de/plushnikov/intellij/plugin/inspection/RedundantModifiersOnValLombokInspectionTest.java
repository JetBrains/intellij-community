package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.RecursionManager;
import de.plushnikov.intellij.plugin.inspection.modifiers.RedundantModifiersOnValLombokAnnotationInspection;

public class RedundantModifiersOnValLombokInspectionTest extends LombokInspectionTest {

  @Override
  protected String getTestDataPath() {
    return TEST_DATA_INSPECTION_DIRECTORY + "/redundantModifierInspection";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantModifiersOnValLombokAnnotationInspection();
  }

  public void testValClass() {
    doTest();
  }

}
