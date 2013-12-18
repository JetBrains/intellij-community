package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;

public class InstanceofPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "testData/templates/instanceof"; }

  public void testSingleExpression()  { doTest(); }
  public void testAlias()             { doTest(); }

  public void testSingleExpressionTemplate() {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
    
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.type('\t');

    TemplateState templateState = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
    assertNotNull(templateState);
    assertFalse(templateState.isFinished());

    myFixture.type("Integer");
    templateState.nextTab();
    assertTrue(templateState.isFinished());

    myFixture.checkResultByFile(getTestName(true) + "_after.java", true);
  }
}
