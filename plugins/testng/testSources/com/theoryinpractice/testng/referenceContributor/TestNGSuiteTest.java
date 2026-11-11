// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.referenceContributor;

import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class TestNGSuiteTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, PluginPathManager.getPluginHomePathRelative("testng") + "/testData/references");

  @Test
  public void testNothing(){}

  @Test
  public void testTestNGSuiteFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package org.testng.annotations; public @interface DataProvider {}");
    myFixture.addClass("package org.testng.annotations; public @interface Test {}");
    myFixture.addClass("package o; @Test public class MyTest { public void testMe(){} }");
    myFixture.addFileToProject("subPack/test-unit.xml", "<suite>" +
                                                        "<test>" +
                                                        "<classes></classes>" +
                                                        "</test>" +
                                                        "</suite>");
    myFixture.enableInspections(new XmlPathReferenceInspection(), new XmlUnresolvedReferenceInspection());
    myFixture.testHighlighting("testng.xml");
  }
}
