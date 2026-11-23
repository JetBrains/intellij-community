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
import org.intellij.plugins.markdown.editor.tables.MarkdownTestSettingsUtilsKt;
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings.EmphasisStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/markdown/test/data/actions/toggleItalic")
public class MarkdownToggleItalicTest {
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
  public void testMultiCaretWordsOn() {
    doTest();
  }

  @Test
  public void testMultiCaretWordsOff() {
    doTest();
  }

  @Test
  public void testPartOfWordOn() {
    doTest();
  }

  @Test
  public void testPartiallyOff() {
    doTest();
  }

  @Test
  public void testSimpleWithUnderscore() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    MarkdownTestSettingsUtilsKt.withEmphasisStyle(EmphasisStyle.UNDERSCORES, () -> {
      myFixture.configureByText("some.md", "Simple <selection>test</selection> text");
      EdtTestUtil.runInEdtAndWait(() -> {
        MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_ITALIC);
        myFixture.checkResult("Simple _<selection>test</selection>_ text");
      });
    });
  }

  private void doTest() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + "_before.md");
    EdtTestUtil.runInEdtAndWait(() -> {
      MarkdownEditorTestUtil.executeAction(myFixture, MarkdownStylingActionIds.TOGGLE_ITALIC);
      myFixture.checkResultByFile(testName + "_after.md");
    });
  }
}
