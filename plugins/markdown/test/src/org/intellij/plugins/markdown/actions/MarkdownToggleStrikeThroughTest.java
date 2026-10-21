// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.actions;

import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.jetbrains.annotations.NotNull;

public class MarkdownToggleStrikeThroughTest extends LightPlatformCodeInsightTestCase {

  public void testSimple() {
    doTest();
  }

  public void testSimpleNoSelection() {
    doTest();
  }

  public void testSimpleSelection() {
    doTest();
  }

  public void testSimpleSelectionInWord() {
    doTest();
  }

  public void testSimpleSelectionInList() {
    doTest();
  }

  public void testSimpleSelectionInQuote() {
    doTest();
  }

  public void testTableRowSelection() {
    configureFromFileText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings | yes | yes |
      | React | no | yes |
      """.trim());
    executeAction("Markdown.Table.SelectRow");
    executeAction(MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
    checkResultByText("""
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | ~~Headings~~ | ~~yes~~ | ~~yes~~ |
      | React | no | yes |
      """.trim());
  }

  public void testTableColumnSelection() {
    configureFromFileText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings | yes | yes |
      | React | no | yes |
      """.trim());
    executeAction("Markdown.Table.SelectCurrentColumn.SelectContentCells");
    executeAction(MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
    checkResultByText("""
      | ~~Feature~~ | Markdown | MDX |
      |---------|----------|-----|
      | ~~Headings~~ | yes | yes |
      | ~~React~~ | no | yes |
      """.trim());
  }

  public void testTableRowSelectionCanToggleOff() {
    configureFromFileText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings | yes | yes |
      | React | no | yes |
      """.trim());
    executeAction("Markdown.Table.SelectRow");
    executeAction(MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
    executeAction(MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
    checkResultByText("""
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | Headings | yes | yes |
      | React | no | yes |
      """.trim());
  }

  public void testTableRowSelectionKeepsExistingFormatting() {
    configureFromFileText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings | ~~yes~~ | no |
      | React | no | yes |
      """.trim());
    executeAction("Markdown.Table.SelectRow");
    executeAction(MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
    checkResultByText("""
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | ~~Headings~~ | ~~yes~~ | ~~no~~ |
      | React | no | yes |
      """.trim());
  }

  public void testTableRowSelectionSkipsEmptyCells() {
    configureFromFileText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings |   | no |
      | React | no | yes |
      """.trim());
    executeAction("Markdown.Table.SelectRow");
    executeAction(MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
    checkResultByText("""
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | ~~Headings~~ |   | ~~no~~ |
      | React | no | yes |
      """.trim());
  }

  private void doTest() {
    configureByFile(getTestName(true) + "_before.md");
    executeAction(MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH);
    checkResultByFile(getTestName(true) + "_after.md");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/actions/toggleStrikethrough/";
  }
}