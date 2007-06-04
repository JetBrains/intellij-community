/*
 * User: anna
 * Date: 04-Jun-2007
 */
package com.theoryinpractice.testng.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.theoryinpractice.testng.inspection.ConvertOldAnnotationInspection;
import org.testng.annotations.Test;

public class ConvertOldAnnotationInspectionTest extends BaseTestNGInspectionsTest{
  protected String getSourceRoot() {
    return "configuration";
  }

  protected LocalInspectionTool getEnabledTool() {
    return new ConvertOldAnnotationInspection();
  }

  @Test
  public void test1() throws Throwable {
    doTest("1");
  }
}