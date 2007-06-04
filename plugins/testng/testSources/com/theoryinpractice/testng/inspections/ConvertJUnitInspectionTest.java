/*
 * User: anna
 * Date: 04-Jun-2007
 */
package com.theoryinpractice.testng.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.theoryinpractice.testng.inspection.JUnitConvertTool;
import org.testng.annotations.Test;

public class ConvertJUnitInspectionTest extends BaseTestNGInspectionsTest{
  protected String getSourceRoot() {
    return "junit";
  }

  protected LocalInspectionTool getEnabledTool() {
    return new JUnitConvertTool();
  }

  protected String getActionName() {
    return JUnitConvertTool.QUICKFIX_NAME;
  }

  @Test (invocationCount = 10)
  public void test1() throws Throwable {
    doTest("Class");
  }

  @Test (invocationCount = 10)
  public void test2() throws Throwable {
    doTest("Fail");
  }
}