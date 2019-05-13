/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.theoryinpractice.testng.inspection;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestNGDependsOnInspectionTest extends LightCodeInsightFixtureTestCase {
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
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
        try {
          TestNGDependsOnInspectionTest.super.setUp();
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
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
        try {
          TestNGDependsOnInspectionTest.super.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    }
  
}
