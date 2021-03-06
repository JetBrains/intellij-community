package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.ui.TestDialog;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class LombokLoggerActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new LombokLoggerAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/lombok/log";
  }

  public void testLogSimple() throws Exception {
    setTestDialog(TestDialog.DEFAULT);
    doTest();
  }

  public void testLogRename() throws Exception {
    setTestDialog(TestDialog.OK);
    doTest();
  }

  public void testLogPublic() throws Exception {
    setTestDialog(TestDialog.OK);
    doTest();
  }

  public void testLogNonStatic() throws Exception {
    setTestDialog(TestDialog.OK);
    doTest();
  }

  public void testLogNonFinal() throws Exception {
    setTestDialog(TestDialog.OK);
    doTest();
  }

  private void setTestDialog(TestDialog newValue) {
    try {
      // TestDialogManager.setTestDialog(newValue); IntelliJ>=2020.3
      callPerReflection("com.intellij.openapi.ui.TestDialogManager", newValue);
    } catch (Exception ignore) {
      try {
        // Messages.setTestDialog(newValue); IntelliJ<=2020.2
        callPerReflection("com.intellij.openapi.ui.Messages", newValue);
      } catch (Exception ignore2) {
        fail("No supported DialogManager classes found!");
      }
    }
  }

  private void callPerReflection(String className, TestDialog newValue) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Class<?> dialogManagerClass = Class.forName(className);
    Method setTestDialogMethod = dialogManagerClass.getDeclaredMethod("setTestDialog", TestDialog.class);
    setTestDialogMethod.invoke(null, newValue);
  }
}
