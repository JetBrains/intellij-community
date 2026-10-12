// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class XmlCommentByLineTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testMethodName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(XmlTestUtil.getXmlTestDataPath());
    testMethodName = testInfo.getTestMethod().orElseThrow().getName();
  }

  private void doTest() {
    CommentByLineTestUtil.doTest(codeInsightFixture.get(), testMethodName);
  }

  private void doInvertedTest() {
    CommentByLineTestUtil.doInvertedTest(codeInsightFixture.get(), testMethodName, 1);
  }

  private static @NotNull CommonCodeStyleSettings getLanguageSettings(@NotNull Language language) {
    return CodeStyle.getSettings(projectFixture.get()).getCommonSettings(language);
  }

  @Test
  public void testXml1() {
    doTest();
  }

  @Test
  public void testXml2() {
    doTest();
  }

  @Test
  public void testXml3() {
    doTest();
  }

  @Test
  public void testXml4() {
    getLanguageSettings(XMLLanguage.INSTANCE).LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  @Test
  public void testXml5() {
    getLanguageSettings(XMLLanguage.INSTANCE).LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  @Test
  public void testXml6() {
    doTest();
  }

  @Test
  public void testXml7() {
    doTest();
  }

  @Test
  public void testXml8() {
    doTest();
  }
  @Test
  public void testXml9() {
    doTest();
  }

  @Test
  public void testXml10() { // this is a feature
    doTest();
  }

  @Test
  public void testXml11() {
    doTest();
  }

  @Test
  public void testXml_DoubleDash() {
    doInvertedTest();
  }

  @Test
  public void testXml_SingleDash() {
    doTest();
  }

  @Test
  public void testXml_SingleDashWithSpace() {
    doTest();
  }

  @Test
  public void testXml_OtherLine() {
    doTest();
  }

  @Test
  public void testHtml1() {
    doTest();
  }

  @Test
  public void testHtml2() {
    doTest();
  }

  @Test
  public void testHtml3() {
    doTest();
  }

  @Test
  public void testHtml4() {
    getLanguageSettings(HTMLLanguage.INSTANCE).LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  @Test
  public void testHtml5() {
    getLanguageSettings(HTMLLanguage.INSTANCE).LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  @Test
  public void testHtml6() {
    doTest();
  }

  @Test
  public void testHtml7() {
    doTest();
  }

  @Test
  public void testHtml8() {
    doTest();
  }

  @Test
  public void testHtml9() {
    doTest();
  }

  @Test
  public void testHtml10() {
    doTest();
  }
}
