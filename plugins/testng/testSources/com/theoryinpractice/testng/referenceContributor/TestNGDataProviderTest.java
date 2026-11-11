// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.referenceContributor;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@Test
@TestApplication
public class TestNGDataProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, PluginPathManager.getPluginHomePathRelative("testng") + "/testData/references");

  @DataProvider
  public Object[][] data() {
    return new Object[][]{
      new Object[]{"private", new String[]{"data"}},
      new Object[]{"privateinsuper", ArrayUtilRt.EMPTY_STRING_ARRAY},
      new Object[]{"protectedinsuper", new String[]{"data"}},
      new Object[]{"privateindataproviderclass", new String[]{"data"}},
    };
  }

  @org.junit.jupiter.api.Test
  public void testNothing(){}

  @Test(dataProvider = "data")
  public void checkDataProviders(final String path, final String... results) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package org.testng.annotations; public @interface DataProvider {}");
    myFixture.addClass("package org.testng.annotations; public @interface Test {}");
    myFixture.testCompletionVariants(path + "provider.java", results);
  }
}
