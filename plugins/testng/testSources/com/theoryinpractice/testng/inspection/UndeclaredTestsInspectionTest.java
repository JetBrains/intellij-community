/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * User: anna
 * Date: 18-Jun-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.InspectionTestCase;
import org.jetbrains.annotations.NonNls;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class UndeclaredTestsInspectionTest extends InspectionTestCase {

  @Override
  public String getName() {
    return "test";
  }

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
    return PluginPathManager.getPluginHomePath("testng") + "/testData/inspection";
  }

  @DataProvider
  public Object[][] data() {
    return new Object[][]{{"declared"}, {"undeclared"}, {"packageDeclared"}, {"packageNonDeclared"}, {"commented"}, {"commented1"}};
  }

  @Test(dataProvider = "data")
  public void doTest(String name) throws Exception {
    doTest("undeclaredTests/" + name, new UndeclaredTestInspection());
  }

  /**
   * @see junit.framework.TestSuite warning
   */
  public void test() {}
}