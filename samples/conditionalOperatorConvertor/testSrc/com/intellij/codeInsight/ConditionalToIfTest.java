/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 22, 2002
 * Time: 2:58:42 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.ConditionalOperatorConvertor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class ConditionalToIfTest extends LightCodeInsightTestCase {
  private ConditionalOperatorConvertor myAction;
  private static final String BASE_PATH = "/codeInsight/conditionalToIf/";
  private boolean myElseOnNewLine;

  protected void setUp() throws Exception {
    super.setUp();

    myAction = new ConditionalOperatorConvertor();
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    myElseOnNewLine = settings.ELSE_ON_NEW_LINE;
    settings.ELSE_ON_NEW_LINE = true;
  }

  protected void tearDown() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.ELSE_ON_NEW_LINE = myElseOnNewLine;

    super.tearDown();
  }

  public void testAssign() throws Exception { doTest(); }

  public void testDeclaration() throws Exception { doTest(); }


  private void doTest() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    assertTrue(myAction.isAvailable(getProject(), getEditor(), getFile()));
    myAction.invoke(getProject(), getEditor(), getFile());
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

}
