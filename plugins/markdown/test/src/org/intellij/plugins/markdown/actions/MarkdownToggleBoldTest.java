package org.intellij.plugins.markdown.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.TestActionEvent;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.intellij.plugins.markdown.editor.tables.MarkdownTestSettingsUtilsKt;
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings.EmphasisStyle;
import org.jetbrains.annotations.NotNull;

public class MarkdownToggleBoldTest extends LightPlatformCodeInsightTestCase {

  public void testSimple() {
    doTest();
  }

  public void testSimpleWithUnderscores() {
    MarkdownTestSettingsUtilsKt.withEmphasisStyle(EmphasisStyle.UNDERSCORES, () -> {
      configureFromFileText("some.md", "<caret>");
      executeAction(MarkdownStylingActionIds.TOGGLE_BOLD);
      checkResultByText("__<caret>__");
    });
  }

  public void testCaretInsideBoldIsSelected() {
    configureFromFileText("some.md", "**fo<caret>o**");
    AnAction action = ActionManager.getInstance().getAction(MarkdownStylingActionIds.TOGGLE_BOLD);
    DataContext context = SimpleDataContext.builder()
      .add(CommonDataKeys.EDITOR, getEditor())
      .add(CommonDataKeys.PSI_FILE, getFile())
      .add(CommonDataKeys.PROJECT, getProject())
      .build();
    assertTrue(((ToggleAction)action).isSelected(TestActionEvent.createTestEvent(
      action,
      context
    )));
  }

  public void testTableRowSelectionCanToggleBoldAfterItalic() {
    configureFromFileText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings | yes | yes |
      | React | no | yes |
      """.trim());
    executeAction("Markdown.Table.SelectRow");
    executeAction(MarkdownStylingActionIds.TOGGLE_BOLD);
    executeAction(MarkdownStylingActionIds.TOGGLE_ITALIC);
    executeAction(MarkdownStylingActionIds.TOGGLE_BOLD);
    checkResultByText("""
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | *Headings* | *yes* | *yes* |
      | React | no | yes |
      """.trim());
  }

  public void testTableRowSelectionCanToggleBoldAfterHighlight() {
    configureFromFileText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>==**Headings**== | ==**yes**== | ==**yes**== |
      | React | no | yes |
      """.trim());
    executeAction("Markdown.Table.SelectRow");
    executeAction(MarkdownStylingActionIds.TOGGLE_BOLD);
    checkResultByText("""
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | ==Headings== | ==yes== | ==yes== |
      | React | no | yes |
      """.trim());
  }

  private void doTest() {
    configureByFile(getTestName(true) + "_before.md");
    executeAction(MarkdownStylingActionIds.TOGGLE_BOLD);
    checkResultByFile(getTestName(true) + "_after.md");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/actions/toggleBold/";
  }
}
