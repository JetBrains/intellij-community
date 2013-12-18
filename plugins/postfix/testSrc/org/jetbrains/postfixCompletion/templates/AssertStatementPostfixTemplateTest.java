package org.jetbrains.postfixCompletion.templates;

public class AssertStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testBooleanVariableBeforeAssignment() { doTest(); }
  public void testBoxedBooleanVariable() { doTest(); }
  public void testNotBooleanExpression() { doTest(); }
  public void testUnresolvedVariable() { doTest(); }
  public void testSeveralConditions() { doTest(); }
  public void testIntegerComparison() { doTest(); }
  public void testMethodInvocation() { doTest(); }
  public void testInstanceof() { doTest(); }
  public void testInstanceofBeforeReturnStatement() { doTest(); }
  public void testNotNull() { doTest(); }

  @Override
  protected String getTestDataPath() { return "testData/templates/assert"; }
}
