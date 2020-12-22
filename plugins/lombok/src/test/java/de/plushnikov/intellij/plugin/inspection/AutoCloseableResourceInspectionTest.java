package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.resources.AutoCloseableResourceInspection;

public class AutoCloseableResourceInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/autoCloseableResource";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new AutoCloseableResourceInspection();
  }

  public void testAutoCloseableCleanup() {
    doTest();
  }

}
