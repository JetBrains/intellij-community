package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class LombokLoggerActionTest extends LightCodeInsightTestCase {

  public void testLogSimple() throws Exception {
    doTest();
  }

  public void testLogRename() throws Exception {
    Messages.setTestDialog(TestDialog.OK);
    doTest();
  }

  public void testLogPublic() throws Exception {
    doTest();
  }

  public void testLogNonStatic() throws Exception {
    doTest();
  }

  public void testLogNonFinal() throws Exception {
    doTest();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return "./lombok-plugin/src/test/data";
  }

  protected void doTest() throws Exception {
    configureByFile("/action/lombok/log/before" + getTestName(false) + ".java");
    performTest();
    checkResultByFile("/action/lombok/log/after" + getTestName(false) + ".java");
  }

  private void performTest() {
    LombokLoggerAction anAction = new LombokLoggerAction();
    anAction.actionPerformed(AnActionEvent.createFromInputEvent(anAction, null, ""));
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
