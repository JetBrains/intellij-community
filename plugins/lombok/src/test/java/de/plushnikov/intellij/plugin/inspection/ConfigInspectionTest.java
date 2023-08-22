package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

/**
 * @author Plushnikov Michail
 */
public class ConfigInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/equalsAndHashCodeCallSuperConfigSkip";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testTest() {
    myFixture.configureByFile("lombok.config");
    doTest();
  }
}
