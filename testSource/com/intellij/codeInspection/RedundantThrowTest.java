package com.intellij.codeInspection;

import com.intellij.codeInspection.unneededThrows.UnneededThrows;


public class RedundantThrowTest extends InspectionTestCase {
  private void doTest() throws Exception {
    doTest("redundantThrow/" + getTestName(false), getManager().getCurrentProfile().getInspectionTool(UnneededThrows.SHORT_NAME));
  }

  public void testSCR8322() throws Exception { doTest(); }

  public void testSCR6858() throws Exception { doTest(); }

  public void testSCR14543() throws Exception { doTest(); }
}
