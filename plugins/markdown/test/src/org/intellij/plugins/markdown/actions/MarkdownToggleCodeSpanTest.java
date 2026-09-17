package org.intellij.plugins.markdown.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.jetbrains.annotations.NotNull;

public class MarkdownToggleCodeSpanTest extends LightPlatformCodeInsightTestCase {
  private static final String ACTION_ID = "org.intellij.plugins.markdown.ui.actions.styling.ToggleCodeSpanAction";

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

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean actionIsEnabled) {
    configureByFile(getTestName(true) + "_before.md");
    AnAction action = ActionManager.getInstance().getAction(ACTION_ID);
    assertEquals(actionIsEnabled, EditorTestUtil.checkActionIsEnabled(getEditor(), action));
    if (actionIsEnabled) {
      executeAction(ACTION_ID);
    }
    checkResultByFile(getTestName(true) + "_after.md");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/actions/toggleCodeSpan/";
  }
}
