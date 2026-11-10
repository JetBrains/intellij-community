/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class MalformedDataProviderInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testMissedDataProvider() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();

    myFixture.testHighlighting(true, false, false, "MissedDataProvider.java");
  }

  @Test
  public void testInstanceDataProviderFromForeignClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(true, false, false, "InstanceDataProviderFromForeignClass.java");
  }

  @Test
  public void testClassLevelDataProviderClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(true, false, false, "ClassLevelDataProviderClass.java");
  }

  @Test
  public void testAbstractClassProvider() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(true, false, false, "AbstractClassProvider.java");
  }

  @Test
  public void testInheritedClassLevelDataProviderClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(true, false, false, "InheritedClassLevelDataProviderClass.java");
  }

  @Test
  public void testSubclassInheritsDataProvider() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(true, false, false, "testSubclassInheritsDataProvider.java");
  }

  @Test
  public void testSubclassInheritsDataProviderQuickFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    List<IntentionAction> fixes = myFixture.getAllQuickFixes("testSubclassInheritsDataProviderFix.java");
    IntentionAction fix = ReadAction.computeBlocking(() -> fixes.stream().filter(f -> f.getText().startsWith("Create method")).findFirst().orElseThrow());
    myFixture.launchAction(fix);
    myFixture.checkResultByFile("testSubclassInheritsDataProviderFix_after.java");
  }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(PluginPathManager.getPluginHomePath("testng") + "/testData/inspection/dataProvider/");
    myFixture.addClass("""
                         package org.testng.annotations;
                         public @interface DataProvider {
                           String name() default "";
                         }""");
    myFixture.addClass("""
                         package org.testng.annotations;
                         public @interface Test {
                           String dataProvider() default {};
                           Class dataProviderClass() default {};
                         }""");
    myFixture.enableInspections(new MalformedDataProviderInspection());
  }
}
