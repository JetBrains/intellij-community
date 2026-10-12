// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.defaultAction;

import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class XmlQuoteTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(XmlTestUtil.getXmlTestDataPath());
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
  }

  @Test
  public void testAttributeStart() { doTest('"'); }
  @Test
  public void testAttributeEnd() { doTest('"'); }

  private void doTest(char c) {
    String path = "codeInsight/defaultAction/quote/";
    configureByFile(path + testName + ".xml");
    performAction(c);
    checkResultByFile(path + testName + "_after.xml");
  }

  private void configureByFile(String filePath) {
    codeInsightFixture.get().configureByFile(filePath);
  }

  private void performAction(char c) {
    DefaultActionTestUtil.performAction(codeInsightFixture.get(), c);
  }

  private void checkResultByFile(String filePath) {
    codeInsightFixture.get().checkResultByFile(filePath);
  }
}
