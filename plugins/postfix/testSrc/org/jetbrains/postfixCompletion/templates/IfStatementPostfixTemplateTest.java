package org.jetbrains.postfixCompletion.templates;

import com.intellij.testFramework.TestDataPath;

@TestDataPath("$CONTENT_ROOT/plugins/postfix/testData/templates/if")
public class IfStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testBooleanVariableBeforeAssignment() { doTest(); }
  public void _testBoxedBooleanVariable() { doTest(); } //todo: platform changes if required
  public void testNotBooleanExpression() { doTest(); }
  public void testUnresolvedVariable() { doTest(); }
  public void testSeveralConditions() { doTest(); }
  public void testIntegerComparison() { doTest(); }
  public void testMethodInvocation() { doTest(); }
  public void testInstanceof() { doTest(); }
  public void testInstanceofBeforeReturnStatement() { doTest(); }

  @Override
  protected String getTestDataPath() { return "plugins/postfix/testData/templates/if"; }
}
