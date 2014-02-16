package de.plushnikov.intellij.plugin.action;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import de.plushnikov.lombok.LombokLightCodeInsightTestCase;

import java.io.File;
import java.io.IOException;

public abstract class LombokLightActionTest extends LombokLightCodeInsightTestCase {
  protected void doTest() throws Exception {
    myFixture.configureByFile(getBasePath() + "/before" + getTestName(false) + ".java");
    performActionTest();
    checkResultByFile(getBasePath() + "/after" + getTestName(false) + ".java");
  }

  private void checkResultByFile(String expectedFile) throws IOException {
    try {
      myFixture.checkResultByFile(expectedFile, true);
    } catch (FileComparisonFailure ex) {
      String actualFileText = myFixture.getFile().getText();
      actualFileText = actualFileText.replace("java.lang.", "");

      final String path = getTestDataPath() + "/" + expectedFile;
      String expectedFileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(path)));

      assertEquals(expectedFileText.replaceAll("\\s+", ""), actualFileText.replaceAll("\\s+", ""));
    }
  }

  private void performActionTest() {
    AnAction anAction = getAction();

    DataContext context = DataManager.getInstance().getDataContext();
    AnActionEvent anActionEvent = new AnActionEvent(null, context, "", anAction.getTemplatePresentation(), ActionManager.getInstance(), 0);

    anAction.actionPerformed(anActionEvent);
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  protected abstract AnAction getAction();
}
