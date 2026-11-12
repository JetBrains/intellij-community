package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/xml/tests/testData/codeInsight/template/emmet")
public class XmlZenCodingUndoTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @Test
  public void testUndoWi2356(TestInfo testInfo) {
    String testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
    EdtTestUtil.runInEdtAndWait(() -> {
      CodeInsightTestFixture myFixture = codeInsightFixture.get();
      UndoManager undoManager = UndoManager.getInstance(projectFixture.get());
      myFixture.configureByFile(testName + ".html");
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        TemplateManager.getInstance(projectFixture.get()).startTemplate(myFixture.getEditor(), TemplateSettings.TAB_CHAR);
      });
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      // IndexOutOfBoundException should not be thrown
      undoManager.undo(TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor()));
    });
  }
}
