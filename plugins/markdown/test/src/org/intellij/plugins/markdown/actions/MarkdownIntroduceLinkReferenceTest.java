// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.actions;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.markdown.editor.MarkdownEditorTestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/markdown/test/data/actions/link")
public class MarkdownIntroduceLinkReferenceTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
  }

  @Test
  public void testAutoLink() {
    doTest();
  }

  @Test
  public void testGfmAutoLink() {
    doTest();
  }

  @Test
  public void testInlineLink() {
    doTest();
  }

  @Test
  public void testInlineLinkInList() {
    doTest();
  }

  @Test
  public void testInlineLinkNoTitle() {
    doTest();
  }

  @Test
  public void testLarge() {
    doTest();
  }

  @Test
  public void testComplexLinkText() {
    doTest();
  }

  @Test
  public void testComplexTitle() {
    doTest();
  }

  @Test
  public void testDuplicateGfm() {
    doTest();
  }

  @Test
  public void testDuplicateAutoLink() {
    doTest();
  }

  @Test
  public void testDuplicateInlineLink() {
    doTest();
  }

  @Test
  public void testSameTitles() {
    doTest();
  }

  @Test
  public void testDifferentTitles() {
    doTest();
  }

  private void doTest() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + "_before.md");
    EdtTestUtil.runInEdtAndWait(() -> {
      MarkdownEditorTestUtil.executeAction(myFixture, "org.intellij.plugins.markdown.ui.actions.styling.MarkdownIntroduceLinkReferenceAction");
      myFixture.checkResultByFile(testName + "_after.md");
    });
  }
}
