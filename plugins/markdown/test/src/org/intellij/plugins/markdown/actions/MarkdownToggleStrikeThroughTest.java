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
@TestDataPath("$PROJECT_ROOT/community/plugins/markdown/test/data/actions/toggleStrikethrough")
public class MarkdownToggleStrikeThroughTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
  }

  @Test
  public void testSimple() {
    doTest();
  }

  @Test
  public void testSimpleNoSelection() {
    doTest();
  }

  @Test
  public void testSimpleSelection() {
    doTest();
  }

  @Test
  public void testSimpleSelectionInWord() {
    doTest();
  }

  @Test
  public void testSimpleSelectionInList() {
    doTest();
  }

  @Test
  public void testSimpleSelectionInQuote() {
    doTest();
  }

  @Test
  public void testTableRowSelection() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings | yes | yes |
      | React | no | yes |
      """.trim());
    EdtTestUtil.runInEdtAndWait(() -> {
      MarkdownEditorTestUtil.executeAction(myFixture, "Markdown.Table.SelectRow");
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
      myFixture.checkResult("""
        | Feature | Markdown | MDX |
        |---------|----------|-----|
        | ~~Headings~~ | ~~yes~~ | ~~yes~~ |
        | React | no | yes |
        """.trim());
    });
  }

  @Test
  public void testTableColumnSelection() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings | yes | yes |
      | React | no | yes |
      """.trim());
    EdtTestUtil.runInEdtAndWait(() -> {
      MarkdownEditorTestUtil.executeAction(myFixture, "Markdown.Table.SelectCurrentColumn.SelectContentCells");
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
      myFixture.checkResult("""
        | ~~Feature~~ | Markdown | MDX |
        |---------|----------|-----|
        | ~~Headings~~ | yes | yes |
        | ~~React~~ | no | yes |
        """.trim());
    });
  }

  @Test
  public void testTableRowSelectionCanToggleOff() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings | yes | yes |
      | React | no | yes |
      """.trim());
    EdtTestUtil.runInEdtAndWait(() -> {
      MarkdownEditorTestUtil.executeAction(myFixture, "Markdown.Table.SelectRow");
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
      myFixture.checkResult("""
        | Feature | Markdown | MDX |
        |---------|----------|-----|
        | Headings | yes | yes |
        | React | no | yes |
        """.trim());
    });
  }

  @Test
  public void testTableRowSelectionKeepsExistingFormatting() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings | ~~yes~~ | no |
      | React | no | yes |
      """.trim());
    EdtTestUtil.runInEdtAndWait(() -> {
      MarkdownEditorTestUtil.executeAction(myFixture, "Markdown.Table.SelectRow");
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
      myFixture.checkResult("""
        | Feature | Markdown | MDX |
        |---------|----------|-----|
        | ~~Headings~~ | ~~yes~~ | ~~no~~ |
        | React | no | yes |
        """.trim());
    });
  }

  @Test
  public void testTableRowSelectionSkipsEmptyCells() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings |   | no |
      | React | no | yes |
      """.trim());
    EdtTestUtil.runInEdtAndWait(() -> {
      MarkdownEditorTestUtil.executeAction(myFixture, "Markdown.Table.SelectRow");
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
      myFixture.checkResult("""
        | Feature | Markdown | MDX |
        |---------|----------|-----|
        | ~~Headings~~ |   | ~~no~~ |
        | React | no | yes |
        """.trim());
    });
  }

  private void doTest() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + "_before.md");
    EdtTestUtil.runInEdtAndWait(() -> {
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
      myFixture.checkResultByFile(testName + "_after.md");
    });
  }
}
