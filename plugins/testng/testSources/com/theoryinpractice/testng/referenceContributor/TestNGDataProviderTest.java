// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.referenceContributor;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.UIUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class TestNGDataProviderTest extends LightJavaCodeInsightFixtureTestCase {
  @BeforeMethod
  @Override
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

  @AfterMethod
  @Override
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

  @DataProvider
  public Object[][] data() {
    return new Object[][]{
      new Object[]{"private", new String[]{"data"}},
      new Object[]{"privateinsuper", ArrayUtilRt.EMPTY_STRING_ARRAY},
      new Object[]{"protectedinsuper", new String[]{"data"}},
      new Object[]{"privateindataproviderclass", new String[]{"data"}},
    };
  }

  public void testNothing(){}

  @Test(dataProvider = "data")
  public void checkDataProviders(final String path, final String... results) {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      try {
        myFixture.addClass("package org.testng.annotations; public @interface DataProvider {}");
        myFixture.addClass("package org.testng.annotations; public @interface Test {}");
        myFixture.testCompletionVariants(path + "provider.java", results);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("testng") + "/testData/references";
  }


}
