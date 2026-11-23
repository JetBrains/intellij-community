package org.intellij.plugins.markdown.actions;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EditorTestUtil;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/markdown/test/data/actions/toggleCodeSpan")
public class MarkdownToggleCodeSpanTest {
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
  public void testSurroundCodeWithBackticks() {
    doTest();
  }

  @Test
  public void testSurroundCodeWithBackticksCancel() {
    doTest();
  }

  @Test
  public void testDisabledInsideFencedCodeBlock() {
    doTest(false);
  }

  @Test
  public void testTableRowSelectionDoesNotIncludePadding() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings | yes | yes |
      | React | no | yes |
      """.trim());
    EdtTestUtil.runInEdtAndWait(() -> {
      MarkdownEditorTestUtil.executeAction(myFixture, "Markdown.Table.SelectRow");
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_CODE_SPAN);
      myFixture.checkResult("""
        | Feature | Markdown | MDX |
        |---------|----------|-----|
        | `Headings` | `yes` | `yes` |
        | React | no | yes |
        """.trim());
    });
  }

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean actionIsEnabled) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + "_before.md");
    EdtTestUtil.runInEdtAndWait(() -> {
      AnAction action = ActionManager.getInstance().getAction(MarkdownStylingActionIds.TOGGLE_CODE_SPAN);
      assertEquals(actionIsEnabled, EditorTestUtil.checkActionIsEnabled(myFixture.getEditor(), action));
      if (actionIsEnabled) {
        MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_CODE_SPAN);
      }
      myFixture.checkResultByFile(testName + "_after.md");
    });
  }
}
