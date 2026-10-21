package com.intellij.codeInsight;

import com.intellij.codeInsight.editorActions.CodeBlockEndAction;
import com.intellij.codeInsight.editorActions.CodeBlockStartAction;
import com.intellij.ide.DataManager;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class XmlCodeBlockNavigationTest extends LightJavaCodeInsightTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  private static final String BASE_PATH = "/codeInsight/codeBlockNavigation/";

  private void doTest(String name, String extension) {
    configureByFile(BASE_PATH + name + "." + extension);
    performEndBlockAction();
    checkResultByFile(BASE_PATH + name + "_after." + extension);

    configureByFile(BASE_PATH + name + "_after." + extension);
    performStartBlockAction();
    checkResultByFile(BASE_PATH + name + "." + extension);
  }
  private void performEndBlockAction() {
    EditorActionHandler actionHandler = new CodeBlockEndAction().getHandler();

    actionHandler.execute(getEditor(), null, DataManager.getInstance().getDataContext());
  }

  private void performStartBlockAction() {
    EditorActionHandler actionHandler = new CodeBlockStartAction().getHandler();

    actionHandler.execute(getEditor(), null, DataManager.getInstance().getDataContext());
  }

  public void testEndTagInHtml() {
    doTest("1","html");
  }
  public void testEndTagInXHtml() {
    doTest("1","xhtml");
  }  public void testEndTagInHtml2() {
    doTest("2","html");
  }
  public void testEndTagInXml1() {
    doTest("1", "xml");
  }

  public void testEndTagInXml2() {
    doTest("2", "xml");
  }


}
