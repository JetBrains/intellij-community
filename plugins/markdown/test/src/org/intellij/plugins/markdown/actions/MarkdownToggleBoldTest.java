package org.intellij.plugins.markdown.actions;

import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
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
      executeAction("org.intellij.plugins.markdown.ui.actions.styling.ToggleBoldAction");
      checkResultByText("__<caret>__");
    });
  }

  private void doTest() {
    configureByFile(getTestName(true) + "_before.md");
    executeAction("org.intellij.plugins.markdown.ui.actions.styling.ToggleBoldAction");
    checkResultByFile(getTestName(true) + "_after.md");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/actions/toggleBold/";
  }
}
