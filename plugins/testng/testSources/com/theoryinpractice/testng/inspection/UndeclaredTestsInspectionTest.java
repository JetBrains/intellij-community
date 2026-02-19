// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.theoryinpractice.testng.inspection;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.JavaInspectionTestCase;
import com.intellij.util.ui.UIUtil;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class UndeclaredTestsInspectionTest extends JavaInspectionTestCase {

  @Override
  public String getName() {
    return "test";
  }

  @Override
  @BeforeMethod
  protected void setUp() {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      try {
        super.setUp();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  @AfterMethod
  protected void tearDown() {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      try {
        super.tearDown();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  @NonNls
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("testng") + "/testData/inspection";
  }

  @DataProvider
  public Object[][] data() {
    return new Object[][]{{"declared"}, {"undeclared"}, {"packageDeclared"}, {"inSubPackage"}, {"incorrectSubPackage"}, {"packageNonDeclared"}, {"commented"}, {"commented1"}};
  }

  @Test(dataProvider = "data")
  public void doTest(final String name) {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      try {
        TestNGUtil.hasDocTagsSupport = true;
        doTest("undeclaredTests/" + name, new UndeclaredTestInspection());
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  /**
   * @see junit.framework.TestSuite warning
   */
  public void test() {}
}
