/*
 * User: anna
 * Date: 04-Jun-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

public class ConvertAnnotationInspectionTest extends BaseTestNGInspectionsTest {
  protected String getSourceRoot() {
    return "annotation2javadoc";
  }

  protected LocalInspectionTool getEnabledTool() {
    return new ConvertAnnotationInspection();
  }

  @DataProvider
  public Object[][] data() {
    return new String[][] {
      new String[]{"1"},
      new String[]{"2"},
      new String[]{"3"}
    };
  }

  @Test (dataProvider = "data")
  public void test(String suffix) throws Throwable {
    doTest(suffix);
  }

}