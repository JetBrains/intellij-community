/*
 * User: anna
 * Date: 04-Jun-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.theoryinpractice.testng.inspection.ConvertJavadocInspection;
import org.testng.annotations.Test;

public class ConvertJavadocInspectionTest extends BaseTestNGInspectionsTest{
  protected String getSourceRoot() {
    return "javadoc2Annotation";
  }

  protected LocalInspectionTool getEnabledTool() {
    return new ConvertJavadocInspection();
  }

  @Test
  public void test1() throws Throwable {
    doTest("1");
  }
}