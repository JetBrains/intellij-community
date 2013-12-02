package org.jetbrains.postfixCompletion.templates;

public class AssertStatementPostfixTemplateTest extends PostfixTemplateTestCase {
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
  
  public void testNotNull() throws Exception {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return "testData/templates/assert";
  }
}
