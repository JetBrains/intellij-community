package org.intellij.plugins.markdown.actions;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.markdown.editor.MarkdownEditorTestUtil;
import org.intellij.plugins.markdown.editor.tables.MarkdownTestSettingsUtilsKt;
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings.EmphasisStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/markdown/test/data/actions/toggleBold")
public class MarkdownToggleBoldTest {
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
  public void testSimpleWithUnderscores() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    MarkdownTestSettingsUtilsKt.withEmphasisStyle(EmphasisStyle.UNDERSCORES, () -> {
      myFixture.configureByText("some.md", "<caret>");
      EdtTestUtil.runInEdtAndWait(() -> {
        MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_BOLD);
        myFixture.checkResult("__<caret>__");
      });
    });
  }

  @Test
  public void testCaretInsideBoldIsSelected() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("some.md", "**fo<caret>o**");
    EdtTestUtil.runInEdtAndWait(() -> {
      AnAction action = ActionManager.getInstance().getAction(MarkdownStylingActionIds.TOGGLE_BOLD);
      DataContext context = SimpleDataContext.builder()
        .add(CommonDataKeys.EDITOR, myFixture.getEditor())
        .add(CommonDataKeys.PSI_FILE, myFixture.getFile())
        .add(CommonDataKeys.PROJECT, myFixture.getProject())
        .build();
      assertTrue(((ToggleAction)action).isSelected(TestActionEvent.createTestEvent(
        action,
        context
      )));
    });
  }

  @Test
  public void testCaretInsideLinkLabelDoesNotToggleBold() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("some.md", """
      ![alt][file:ima<caret>ge.png] [text][file:ima<caret>ge.png]

      [file:image.png]: image.png
      """.trim());
    EdtTestUtil.runInEdtAndWait(() -> {
      EditorTestUtil.executeAction(myFixture.getEditor(), MarkdownStylingActionIds.TOGGLE_BOLD, false);
      myFixture.checkResult("""
        ![alt][file:ima<caret>ge.png] [text][file:ima<caret>ge.png]

        [file:image.png]: image.png
        """.trim());
    });
  }

  @Test
  public void testTableRowSelectionCanToggleBoldAfterItalic() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>Headings | yes | yes |
      | React | no | yes |
      """.trim());
    EdtTestUtil.runInEdtAndWait(() -> {
      MarkdownEditorTestUtil.executeAction(myFixture, "Markdown.Table.SelectRow");
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_BOLD);
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_ITALIC);
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_BOLD);
      myFixture.checkResult("""
        | Feature | Markdown | MDX |
        |---------|----------|-----|
        | *Headings* | *yes* | *yes* |
        | React | no | yes |
        """.trim());
    });
  }

  @Test
  public void testTableRowSelectionCanToggleBoldAfterHighlight() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("some.md", """
      | Feature | Markdown | MDX |
      |---------|----------|-----|
      | <caret>==**Headings**== | ==**yes**== | ==**yes**== |
      | React | no | yes |
      """.trim());
    EdtTestUtil.runInEdtAndWait(() -> {
      MarkdownEditorTestUtil.executeAction(myFixture, "Markdown.Table.SelectRow");
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_BOLD);
      myFixture.checkResult("""
        | Feature | Markdown | MDX |
        |---------|----------|-----|
        | ==Headings== | ==yes== | ==yes== |
        | React | no | yes |
        """.trim());
    });
  }

  private void doTest() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + "_before.md");
    EdtTestUtil.runInEdtAndWait(() -> {
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_BOLD);
      myFixture.checkResultByFile(testName + "_after.md");
    });
  }
}
