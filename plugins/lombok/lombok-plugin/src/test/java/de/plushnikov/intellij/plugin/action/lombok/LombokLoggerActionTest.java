package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class LombokLoggerActionTest extends LombokLightActionTestCase {

  protected AnAction getAction() {
    return new LombokLoggerAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/lombok/log";
  }

  public void testLogSimple() throws Exception {
    Messages.setTestDialog(TestDialog.DEFAULT);
    doTest();
  }

  public void testLogRename() throws Exception {
    Messages.setTestDialog(TestDialog.OK);
    doTest();
  }

  public void testLogPublic() throws Exception {
    Messages.setTestDialog(TestDialog.OK);
    doTest();
  }

  public void testLogNonStatic() throws Exception {
    Messages.setTestDialog(TestDialog.OK);
    doTest();
  }

  public void testLogNonFinal() throws Exception {
    Messages.setTestDialog(TestDialog.OK);
    doTest();
  }

}
