// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.inspection;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestNGDependsOnInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Test
  public void testDependencies() {
    Runnable runnable = () -> {
      myFixture.addClass("package org.testng.annotations;\n" +
                         "public @interface AfterSuite {  java.lang.String[] dependsOnMethods() default {};}");
      myFixture.addClass("package org.testng.annotations;\n" +
                         "public @interface BeforeMethod {  java.lang.String[] dependsOnMethods() default {};}");
      myFixture.addClass("package org.testng.annotations;\n" +
                         "public @interface Test {  java.lang.String[] dependsOnMethods() default {};}");
      myFixture.testHighlighting(true, false, false, "Dependencies.java");
    };
    UIUtil.invokeAndWaitIfNeeded(runnable);
  }

  public void testNothing(){}
 

  @NotNull
   @Override
   protected String getTestDataPath() {
     return PluginPathManager.getPluginHomePath("testng") + "/testData/inspection/dependsOn/";
   }
  
  @BeforeMethod
    @Override
    protected void setUp() {
      UIUtil.invokeAndWaitIfNeeded(() -> {
        try {
          super.setUp();
          myFixture.enableInspections(new DependsOnMethodInspection());

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
  
}
