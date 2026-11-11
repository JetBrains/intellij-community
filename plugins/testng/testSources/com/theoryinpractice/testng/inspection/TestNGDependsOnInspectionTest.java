// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

@TestApplication
public class TestNGDependsOnInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testDependencies() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package org.testng.annotations;\n" +
                       "public @interface AfterSuite {  java.lang.String[] dependsOnMethods() default {};}");
    myFixture.addClass("package org.testng.annotations;\n" +
                       "public @interface BeforeMethod {  java.lang.String[] dependsOnMethods() default {};}");
    myFixture.addClass("package org.testng.annotations;\n" +
                       "public @interface Test {  java.lang.String[] dependsOnMethods() default {};}");
    myFixture.testHighlighting(true, false, false, "Dependencies.java");
  }

  @Test
  public void testNothing(){}

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(PluginPathManager.getPluginHomePath("testng") + "/testData/inspection/dependsOn/");
    myFixture.enableInspections(new DependsOnMethodInspection());
  }
}
