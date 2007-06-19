/*
 * User: anna
 * Date: 18-Jun-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.InspectionTestCase;
import org.jetbrains.annotations.NonNls;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

public class UndeclaredTestsInspectionTest extends InspectionTestCase {

  @BeforeMethod
  protected void setUp() throws Exception {
    super.setUp();
  }

  @AfterMethod
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @NonNls
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/testng/testData/inspection";
  }

  @DataProvider
  public Object[][] data() {
    return new Object[][]{{"declared"}, {"undeclared"}, {"packageDeclared"}, {"commented"}, {"commented1"}};
  }

  @Test(dataProvider = "data")
  public void doTest(String name) throws Exception {
    doTest("undeclaredTests/" + name, new UndeclaredTestInspection());
  }
}