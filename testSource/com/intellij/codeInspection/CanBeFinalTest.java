/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 11, 2002
 * Time: 7:51:16 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection;

import com.intellij.codeInspection.canBeFinal.CanBeFinalInspection;

public class CanBeFinalTest extends InspectionTestCase {
  protected void setUp() throws Exception {
    super.setUp();
    CanBeFinalInspection tool = (CanBeFinalInspection)getManager().getCurrentProfile().getInspectionTool(CanBeFinalInspection.SHORT_NAME);
    tool.REPORT_CLASSES = true;
    tool.REPORT_FIELDS = true;
    tool.REPORT_METHODS = true;
  }

  private void doTest() throws Exception {
    doTest("canBeFinal/" + getTestName(false), getManager().getCurrentProfile().getInspectionTool(CanBeFinalInspection.SHORT_NAME));
  }

  public void testsimpleClassInheritance() throws Exception {
    doTest();
  }

  public void testsimpleClassInheritance1() throws Exception {
    doTest();
  }

  public void testmethodInheritance() throws Exception {
    doTest();
  }

  public void testprivateInners() throws Exception {
    doTest();
  }

  public void testfieldAndTryBlock() throws Exception {
    doTest();
  }

  public void testfields() throws Exception {
    doTest();
  }

  public void testSCR6073() throws Exception {
    doTest();
  }

  public void testSCR6781() throws Exception {
    doTest();
  }

  public void testSCR6845() throws Exception {
    doTest();
  }

  public void testSCR6861() throws Exception {
    doTest();
  }

  public void testSCR7737() throws Exception {
    CanBeFinalInspection tool = (CanBeFinalInspection)getManager().getCurrentProfile().getInspectionTool(CanBeFinalInspection.SHORT_NAME);
    tool.REPORT_CLASSES = false;
    tool.REPORT_FIELDS = false;
    tool.REPORT_METHODS = true;

    doTest();
  }
}
