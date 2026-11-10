// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.inspection;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ExpectedExceptionNeverThrownTestNGInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSimple() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(true, false, false, "Simple.java");
  }

  @Test
  public void testArrayInitializerMemberValue() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(true, false, false, "ArrayInitializerMemberValue.java");
  }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(PluginPathManager.getPluginHomePath("testng") + "/testData/inspection/expected_exception_never_thrown/");
    myFixture.addClass("""
                         package org.testng.annotations;
                         public @interface Test {
                           Class[] expectedExceptions() default {};
                         }""");
    myFixture.enableInspections(new ExpectedExceptionNeverThrownTestNGInspection());
  }
}
