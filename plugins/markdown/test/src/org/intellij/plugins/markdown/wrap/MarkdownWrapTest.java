// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.wrap;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.highlighting.BackgroundHighlighter;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.awt.datatransfer.StringSelection;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/markdown/test/data/wrap")
public class MarkdownWrapTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private boolean myOldWrap;
  private int myOldMargin;

  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
    final CodeStyleSettings settings = CodeStyle.getSettings(codeInsightFixture.get().getProject());
    final CommonCodeStyleSettings commonCodeStyleSettings = settings.getCommonSettings(MarkdownLanguage.INSTANCE);
    myOldWrap = settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
    myOldMargin = commonCodeStyleSettings.RIGHT_MARGIN;
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    commonCodeStyleSettings.RIGHT_MARGIN = 80;
  }

  @AfterEach
  void tearDown() {
    final CodeStyleSettings settings = CodeStyle.getSettings(codeInsightFixture.get().getProject());
    final CommonCodeStyleSettings settingsCommonSettings = settings.getCommonSettings(MarkdownLanguage.INSTANCE);
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = myOldWrap;
    settingsCommonSettings.RIGHT_MARGIN = myOldMargin;
  }

  @Test
  public void testWrapInList() {
    doTest(" a b c");
  }

  @Test
  public void testReformatAfterLongFirstListWord() {
    doTestWithReformat("A".repeat(80));
  }

  @Test
  public void testWrapInQuotes() {
    doTest("synchronization");
  }

  @Test
  public void testWrapInCodeFence() {
    doTest("a b c");
  }

  /** Fence content is literal, so a `#` at the start of a fenced line is not a header and the line is wrappable. */
  @Test
  public void testWrapInCodeFenceWithHash() {
    doTest("a b c");
  }

  @Test
  public void testWrapInCodeFenceInQuotes() {
    doTest("a b c d e f");
  }

  @Test
  public void testWrapInCodeFenceInList() {
    doTest("synchronization");
  }

  @Test
  public void testWrapInHeader() {
    doTest("synchronization");
  }

  @Test
  public void testWrapInSetextHeader() {
    doTest("synchronization");
  }

  @Test
  public void testWrapInTable() {
    doTest("synchronization");
  }

  /** A `|` outside of a table does not make the line a table row, so wrapping is still allowed. */
  @Test
  public void testWrapWithPipeInText() {
    doTest("a b c");
  }

  @Test
  public void testWrapDoesNotBreakLink() {
    doTest("a");
  }

  @Test
  public void testWrapDoesNotBreakBareUrl() {
    doTest("a");
  }

  /**
   * The fixture places the address at the right margin after typing one character.
   * The trailing space stays before the newline because the rule wraps after a space.
   */
  @Test
  public void testWrapDoesNotBreakIpv4() {
    doTest("a");
  }

  @Test
  public void testWrapDoesNotBreakLinkWhenTypingInsideLink() {
    doTest("X");
  }

  /** A closed `[` before a bare URL keeps the wrap in front of the URL, so the list marker stays on its line. */
  @Test
  public void testWrapGoesBeforeBareUrlAfterClosedBracket() {
    doTest("2");
  }

  /** Markdown has no string literals, so the word before the wrap stays on the line while it fits in the right margin. */
  @Test
  public void testWrapKeepsPrecedingWordThatFits() {
    doTest("t");
  }

  @Test
  public void testPasteIntoLineWithLinkDoesNotAssert() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("pasteIntoLineWithLinkDoesNotAssert.md");
    EdtTestUtil.runInEdtAndWait(() -> {
      EditorTestUtil.configureSoftWraps(myFixture.getEditor(), 40);

      CopyPasteManager.getInstance().setContents(new StringSelection("aaaa"));
      BackgroundHighlighter.Companion.runWithEnabledListenersInTest(myFixture.getProject(), () -> {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
      });
    });
  }

  @Test
  public void testWrapRightMargin() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    final CommonCodeStyleSettings commonCodeStyleSettings = settings.getCommonSettings(MarkdownLanguage.INSTANCE);
    int oldValue = commonCodeStyleSettings.RIGHT_MARGIN;
    boolean oldMarginValue = settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
    commonCodeStyleSettings.RIGHT_MARGIN = 100;
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    try {
      myFixture.configureByFile(testName + ".md");
      EdtTestUtil.runInEdtAndWait(() -> {
        for (int i = 0; i != 45; ++i) {
          myFixture.type(' ');
        }
        myFixture.checkResultByFile(testName + ".after.md");
      });
    }
    finally {
      commonCodeStyleSettings.RIGHT_MARGIN = oldValue;
      settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = oldMarginValue;
    }
  }

  private void doTest(@NotNull String textToType) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + ".md");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type(textToType);
      myFixture.checkResultByFile(testName + ".after.md");
    });
  }

  private void doTestWithReformat(@NotNull String textToType) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    final MarkdownCustomCodeStyleSettings markdownSettings = settings.getCustomSettings(MarkdownCustomCodeStyleSettings.class);
    boolean oldWrapTextIfLong = markdownSettings.WRAP_TEXT_IF_LONG;
    markdownSettings.WRAP_TEXT_IF_LONG = true;
    try {
      myFixture.configureByFile(testName + ".md");
      EdtTestUtil.runInEdtAndWait(() -> {
        for (char character : textToType.toCharArray()) {
          myFixture.type(character);
        }
        myFixture.type(' ');
        for (char character : "text more words".toCharArray()) {
          myFixture.type(character);
        }
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT);
        myFixture.checkResultByFile(testName + ".after.md");
      });
    }
    finally {
      markdownSettings.WRAP_TEXT_IF_LONG = oldWrapTextIfLong;
    }
  }
}