package com.intellij.codeInspection;

import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;

/**
 * @author max
 */
public class EmptyMethodTest extends InspectionTestCase {
  private void doTest() throws Exception {
    doTest("emptyMethod/" + getTestName(false), getManager().getCurrentProfile().getInspectionTool(EmptyMethodInspection.SHORT_NAME));
  }

  public void testsuperCall() throws Exception {
    doTest();
  }

  public void testexternalOverride() throws Exception {
    doTest();
  }

  public void testSCR8321() throws Exception {
    doTest();
  }
}
