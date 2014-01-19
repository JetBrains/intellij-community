package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class LombokGetterActionTest extends LightCodeInsightTestCase {

  public void testGetterSimple() throws Exception {
    doTest();
  }

  public void testGetterStatic() throws Exception {
    doTest();
  }

  public void testGetterDifferentVisibility() throws Exception {
    doTest();
  }

  public void testGetterWithAnnotationPresentOnClass() throws Exception {
    doTest();
  }

  public void testGetterWithAnnotationChange() throws Exception {
    doTest();
  }


  @NotNull
  @Override
  protected String getTestDataPath() {
    return "./lombok-plugin/src/test/data";
  }

  protected void doTest() throws Exception {
    configureByFile("/action/lombok/getter/before" + getTestName(false) + ".java");
    performTest();
    checkResultByFile("/action/lombok/getter/after" + getTestName(false) + ".java");
  }

  private void performTest() {
    LombokGetterAction anAction = new LombokGetterAction();
    anAction.actionPerformed(AnActionEvent.createFromInputEvent(anAction, null, ""));
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
