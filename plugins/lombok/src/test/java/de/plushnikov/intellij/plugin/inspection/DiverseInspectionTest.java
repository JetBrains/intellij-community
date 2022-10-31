package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

/**
 * @author Plushnikov Michail
 */
public class DiverseInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/diverse";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testDataEqualsAndHashCodeOverride() {
    doTest();
  }

  public void testEqualsAndHashCodeCallSuper() {
    doTest();
  }

  public void testIssue37() {
    doTest();
  }

  public void testSetterOnFinalField() {
    doTest();
  }

  public void testNoArgsConstructorWithRequiredFieldsShouldBeForced() {
    doTest();
  }

}
