package de.plushnikov.lombok;

import com.intellij.testFramework.InspectionTestCase;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;

/**
 * @author Plushnikov Michail
 */
public class InspectionTest extends InspectionTestCase {

  @Override
  protected String getTestDataPath() {
    return "./lombok-plugin/src/test/data/inspection";
  }

  private void doTest() throws Exception {
    doTest(getTestName(true), new LombokInspection());
  }

  public void testIssue37() throws Exception {
    doTest();
  }
}
