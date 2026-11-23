// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.enter;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
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
@TestDataPath("$PROJECT_ROOT/community/plugins/markdown/test/data/enter")
public class MarkdownEnterHandlerTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
  }

  @Test
  public void testQuote() {
    doTest();
  }

  @Test
  public void testNestedQuote() {
    doTest();
  }

  @Test
  public void testCodeFence() {
    doTest();
  }

  @Test
  public void testCodeFenceLang() {
    doTest();
  }

  @Test
  public void testCodeFenceMiddle() {
    doTest();
  }

  @Test
  public void testQuoteInListMiddle() {
    doTest();
  }

  @Test
  public void testQuoteInList() {
    doTest();
  }

  @Test
  public void testQuoteInList1() {
    doTest();
  }

  @Test
  public void testQuoteInListAfterFirstLine() {
    doTest();
  }

  @Test
  public void testQuoteInListWithSpace() {
    doTest();
  }

  @Test
  public void testCodeFenceWithSpace() {
    doTest();
  }

  @Test
  public void testIndentedCodeFence() {
    doTest();
  }

  @Test
  public void testCodeFenceInListWithoutNewline() {
    doTest();
  }

  @Test
  public void testQuoteInListMiddleWithSpace() {
    doTest();
  }

  public void testQuoteInListBeforeItem() {
    doTest();
  }

  public void testQuoteInNestedListBeforeItem() {
    doTest();
  }

  private void doTest() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + ".before.md");
    myFixture.type("\n");
    myFixture.checkResultByFile(testName + ".after.md");
  }
}