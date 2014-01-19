package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class LombokSetterActionTest extends LightCodeInsightTestCase {

  public void testSetterSimple() throws Exception {
    doTest();
  }

  public void testSetterStatic() throws Exception {
    doTest();
  }

  public void testSetterDifferentVisibility() throws Exception {
    doTest();
  }

  public void testSetterWithAnnotationPresentOnClass() throws Exception {
    doTest();
  }

  public void testSetterWithAnnotationChange() throws Exception {
    doTest();
  }


  @NotNull
  @Override
  protected String getTestDataPath() {
    return "./lombok-plugin/src/test/data";
  }

  protected void doTest() throws Exception {
    configureByFile("/action/lombok/setter/before" + getTestName(false) + ".java");
    performTest();
    checkResultByFile("/action/lombok/setter/after" + getTestName(false) + ".java");
  }

  private void performTest() {
    LombokSetterAction anAction = new LombokSetterAction();
    anAction.actionPerformed(AnActionEvent.createFromInputEvent(anAction, null, ""));
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
