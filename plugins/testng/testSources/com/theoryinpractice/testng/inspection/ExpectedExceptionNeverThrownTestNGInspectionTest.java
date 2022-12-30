// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.inspection;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ExpectedExceptionNeverThrownTestNGInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  public void testSimple() {
    myFixture.testHighlighting(true, false, false, "Simple.java");
  }

  public void testArrayInitializerMemberValue() {
    myFixture.testHighlighting(true, false, false, "ArrayInitializerMemberValue.java");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("testng") + "/testData/inspection/expected_exception_never_thrown/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("""
                         package org.testng.annotations;
                         public @interface Test {
                           Class[] expectedExceptions() default {};
                         }""");
    myFixture.enableInspections(new ExpectedExceptionNeverThrownTestNGInspection());
  }
}
