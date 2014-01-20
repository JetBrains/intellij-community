package de.plushnikov.intellij.plugin.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import de.plushnikov.lombok.LombokLightCodeInsightTestCase;

public abstract class LombokLightActionTest extends LombokLightCodeInsightTestCase {
  protected void doTest() throws Exception {
    myFixture.configureByFile(getBasePath() + "/before" + getTestName(false) + ".java");
    performActionTest();
    myFixture.checkResultByFile(getBasePath() + "/after" + getTestName(false) + ".java");
  }

  private void performActionTest() {
    AnAction anAction = getAction();
    anAction.actionPerformed(AnActionEvent.createFromInputEvent(anAction, null, ""));
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  protected abstract AnAction getAction();
}
