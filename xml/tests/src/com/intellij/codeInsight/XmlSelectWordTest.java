// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
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
public class XmlSelectWordTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testMethodName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(XmlTestUtil.getXmlTestDataPath());
    testMethodName = testInfo.getTestMethod().orElseThrow().getName();
  }

  private void doTest(String ext) {
    SelectWordTestUtil.doTest(codeInsightFixture.get(), testMethodName, ext);
  }

  @Test
  public void testHtml1() {
    doTest("html");
  }

  @Test
  public void testHtml2() {
    doTest("html");
  }

  @Test
  public void testHtml3() {
    doTest("html");
  }

  @Test
  public void testHtml4() {
    doTest("html");
  }

  @Test
  public void testHtml5() {
    doTest("html");
  }

  @Test
  public void testHtml6() {
    doTest("html");
  }
  @Test
  public void testHtml7() {
    doTest("html");
  }

  @Test
  public void testHtml8() {
    doTest("html");
  }

  @Test
  public void testHtml9() {
    doTest("html");
  }

  @Test
  public void testHtml10() {
    doTest("html");
  }

  @Test
  public void testHtml11() {
    doTest("html");
  }

  @Test
  public void testHtml12() {
    doTest("html");
  }

  @Test
  public void testHtml13() {
    doTest("html");
  }

  @Test
  public void testHtml14() {
    doTest("html");
  }

  @Test
  public void testSelectPathInStringLiteral() {
    doTest("html");
  }

  @Test
  public void testSelectPathBeforeStringLiteral() {
    doTest("html");
  }

  @Test
  public void testXml1() {
    doTest("xml");
  }
  @Test
  public void testXml11() {
    doTest("xml");
  }

  @Test
  public void testXml2() {
    doTest("xml");
  }

  @Test
  public void testDtd() {
    doTest("dtd");
  }
  @Test
  public void testDtd1() {
    doTest("dtd");
  }

  @Test
  public void testXml3() {
    doTest("xml");
  }

  @Test
  public void testXmlStartLine() {
    doTest("xml");
  }

  @Test
  public void testXmlCommentAboveTag() {
    doTest("xml");
  }
}
