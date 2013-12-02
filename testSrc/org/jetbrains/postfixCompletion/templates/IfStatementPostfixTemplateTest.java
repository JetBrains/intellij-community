package org.jetbrains.postfixCompletion.templates;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

@TestDataPath("$CONTENT_ROOT/testData/templates/if")
public class IfStatementPostfixTemplateTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testBooleanVariableBeforeAssignment() throws Exception {
    doTest();
  }

  public void testNotBooleanExpression() throws Exception {
    doTest();
  }
  
  public void testUnresolvedVariable() throws Exception {
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
    myFixture.type('\t');
    myFixture.checkResultByFile(getTestName(true) + "_after.java", false);
  }

  @Override
  protected String getTestDataPath() {
    return "testData/templates/if";
  }
}
