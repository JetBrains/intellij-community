/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.theoryinpractice.testng.referenceContributor;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class TestNGDataProviderTest extends LightCodeInsightFixtureTestCase {
  @BeforeMethod
  @Override
  protected void setUp() {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        TestNGDataProviderTest.super.setUp();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @AfterMethod
  @Override
  protected void tearDown() {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        TestNGDataProviderTest.super.tearDown();
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
      new Object[]{"privateinsuper", ArrayUtil.EMPTY_STRING_ARRAY},
      new Object[]{"protectedinsuper", new String[]{"data"}},
      new Object[]{"privateindataproviderclass", new String[]{"data"}},
    };
  }

  public void testNothing(){}

  @Test(dataProvider = "data")
  public void test(final String path, final String... results) {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
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
