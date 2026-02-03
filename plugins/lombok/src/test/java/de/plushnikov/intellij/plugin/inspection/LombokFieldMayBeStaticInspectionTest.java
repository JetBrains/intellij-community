package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.performance.FieldMayBeStaticInspection;

public class LombokFieldMayBeStaticInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/canBeStaticInspection";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new FieldMayBeStaticInspection();
  }

  public void testDefault() {
    doTest();
  }
}
