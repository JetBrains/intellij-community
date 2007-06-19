/*
 * User: anna
 * Date: 04-Jun-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.theoryinpractice.testng.inspection.ConvertAnnotationInspection;
import org.testng.annotations.Test;

public class ConvertAnnotationInspectionTest extends BaseTestNGInspectionsTest {
  protected String getSourceRoot() {
    return "annotation2javadoc";
  }

  protected LocalInspectionTool getEnabledTool() {
    return new ConvertAnnotationInspection();
  }

  @Test
  public void test1() throws Throwable {
    doTest("1");
  }

  @Test
  public void test2() throws Throwable {
    doTest("2");
  }

}