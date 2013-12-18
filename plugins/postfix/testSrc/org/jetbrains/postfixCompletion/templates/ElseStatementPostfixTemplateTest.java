package org.jetbrains.postfixCompletion.templates;

import com.intellij.testFramework.TestDataPath;

@TestDataPath("$CONTENT_ROOT/plugins/postfix/testData/templates/if")
public class ElseStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testBooleanVariable() { doTest(); }
  public void testBoxedBooleanVariable() { doTest(); }
  public void testBitOperations() { doTest(); }
  public void testBitOperationsWithMethod() { doTest(); }
  public void testUnresolvedVariable() { doTest(); }
  public void testInstanceof() { doTest(); }
  public void testIntegerComparison() { doTest(); }
  public void testLogicalOperations() { doTest(); }
  public void testNotNull() { doTest(); }

  @Override
  protected String getTestDataPath() { return "plugins/postfix/testData/templates/else"; }
}
