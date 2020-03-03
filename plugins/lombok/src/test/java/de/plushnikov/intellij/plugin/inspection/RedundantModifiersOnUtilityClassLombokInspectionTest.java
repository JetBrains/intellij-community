package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import de.plushnikov.intellij.plugin.inspection.modifiers.RedundantModifiersOnUtilityClassLombokAnnotationInspection;

public class RedundantModifiersOnUtilityClassLombokInspectionTest extends LombokInspectionTest {

  @Override
  protected String getTestDataPath() {
    return TEST_DATA_INSPECTION_DIRECTORY + "/redundantModifierInspection";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantModifiersOnUtilityClassLombokAnnotationInspection();
  }

  public void testUtilityClassClass() {
    doTest();
  }

}
