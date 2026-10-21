package org.intellij.plugins.markdown.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.jetbrains.annotations.NotNull;

public class MarkdownToggleCodeSpanTest extends LightPlatformCodeInsightTestCase {
  public void testSimple() {
    doTest();
  }

  public void testSurroundCodeWithBackticks() {
    doTest();
  }

  public void testSurroundCodeWithBackticksCancel() {
    doTest();
  }

  public void testDisabledInsideFencedCodeBlock() {
    doTest(false);
  }

  public void testTableRowSelectionDoesNotIncludePadding() {
    configureFromFileText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings | yes | yes |
      | React | no | yes |
      """.trim());
    executeAction("Markdown.Table.SelectRow");
    executeAction(MarkdownStylingActionIds.TOGGLE_CODE_SPAN);
    checkResultByText("""
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | `Headings` | `yes` | `yes` |
      | React | no | yes |
      """.trim());
  }

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean actionIsEnabled) {
    configureByFile(getTestName(true) + "_before.md");
    AnAction action = ActionManager.getInstance().getAction(MarkdownStylingActionIds.TOGGLE_CODE_SPAN);
    assertEquals(actionIsEnabled, EditorTestUtil.checkActionIsEnabled(getEditor(), action));
    if (actionIsEnabled) {
      executeAction(MarkdownStylingActionIds.TOGGLE_CODE_SPAN);
    }
    checkResultByFile(getTestName(true) + "_after.md");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/actions/toggleCodeSpan/";
  }
}
