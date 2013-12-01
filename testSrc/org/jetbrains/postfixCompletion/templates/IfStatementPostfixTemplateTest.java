package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

@TestDataPath("$CONTENT_ROOT/testData/templates/if")
public class IfStatementPostfixTemplateTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testBooleanVariableBeforeAssignment() throws Exception {
    doTest();
  }

  public void testSeveralConditions() throws Exception {
    doTest();
  }

  public void testIntegerComparison() throws Exception {
    doTest();
  }

  public void testMethodInvocation() throws Exception {
    doTest();
  }

  public void testInstanceof() throws Exception {
    doTest();
  }

  public void testInstanceofBeforeReturnStatement() throws Exception {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + ".java");
    TemplateManager.getInstance(getProject()).startTemplate(myFixture.getEditor(), TemplateSettings.TAB_CHAR);
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  @Override
  protected String getTestDataPath() {
    return "testData/templates/if";
  }
}
