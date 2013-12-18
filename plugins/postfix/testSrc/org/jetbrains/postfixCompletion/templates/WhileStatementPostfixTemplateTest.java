package org.jetbrains.postfixCompletion.templates;

public class WhileStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testBooleanVariable() { doTest(); }
  public void testBoxedBooleanVariable() { doTest(); }
  public void testStringVariable() { doTest(); }
  public void testUnresolvedVariable() { doTest(); }

  @Override
  protected String getTestDataPath() { return "plugins/postfix/testData/templates/while"; }
}
