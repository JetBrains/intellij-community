// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.configuration;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class TestNGRunMarkerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package org.testng.annotations; @interface Test {String[] dependsOnMethods() default {};}");
  }

  @Test
  public void testSimpleRunMarker() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(
      "SomethingTest.java",
      """
        package org.example;
        
        import org.testng.annotations.Test;

        public class SomethingTest {
        
        
            private int number = 1;
        
            @Test
            public void givenNumber_whenEven_thenTrue() {
            }
        }
        """);
    List<GutterMark> gutters = myFixture.findAllGutters();
    Assertions.assertEquals(2, gutters.size());
  }

  @Test
  public void testSimpleRunMarkerInDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFile file = (PsiJavaFile)myFixture.configureByText(
      "SomethingTest.java",
      """
        package org.example;
      
        import org.testng.annotations.Test;

        public class SomethingTest {
      
      
            private int number = 1;
      
            @Test
            public void givenNumber_whenEven_thenTrue() {
            }
        }
        """);

    PsiClass aClass = ReadAction.computeBlocking(() -> file.getClasses()[0]);
    PsiMethod method = ReadAction.computeBlocking(() -> aClass.getMethods()[0]);
    DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> {
      ReadAction.runBlocking(() -> {
        Assertions.assertTrue(TestFrameworks.getInstance().isTestClass(aClass));
        Assertions.assertTrue(TestFrameworks.getInstance().isTestMethod(method));
      });
    });
  }
}
