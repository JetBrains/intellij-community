/*
 * User: anna
 * Date: 04-Jun-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ConvertJavadocInspectionTest extends BaseTestNGInspectionsTest{
  protected String getSourceRoot() {
    return "javadoc2Annotation";
  }

  protected LocalInspectionTool getEnabledTool() {
    return new ConvertJavadocInspection();
  }

  @DataProvider
  public Object[][] data() {
    return new String[][]{new String[]{"1"}, new String[]{"2"}, new String[]{"3"}};
  }

  @Test (dataProvider = "data")
  public void test(String suffix) throws Throwable {
    doTest(suffix);
  }
}