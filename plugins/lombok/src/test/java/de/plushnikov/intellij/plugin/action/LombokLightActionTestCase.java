package de.plushnikov.intellij.plugin.action;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.AsyncResult;
import de.plushnikov.intellij.plugin.LombokLightCodeInsightTestCase;

public abstract class LombokLightActionTestCase extends LombokLightCodeInsightTestCase {
  protected void doTest() throws Exception {
    myFixture.configureByFile(getBasePath() + "/before" + getTestName(false) + ".java");
    performActionTest();
    checkResultByFile(getBasePath() + "/after" + getTestName(false) + ".java");
  }

  protected void performActionTest() {
    AnAction anAction = getAction();

    AsyncResult<DataContext> contextResult = DataManager.getInstance().getDataContextFromFocus();
    AnActionEvent anActionEvent = new AnActionEvent(null, contextResult.getResult(), "", anAction.getTemplatePresentation(), ActionManager.getInstance(), 0);

    anAction.actionPerformed(anActionEvent);
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  protected abstract AnAction getAction();
}
