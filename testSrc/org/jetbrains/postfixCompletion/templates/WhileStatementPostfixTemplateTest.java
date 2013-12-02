package org.jetbrains.postfixCompletion.templates;

public class WhileStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testBooleanVariable() { doTest(); }
  public void testStringVariable() { doTest(); }
  public void testUnresolvedVariable() { doTest(); }

  @Override
  protected String getTestDataPath() { return "testData/templates/while"; }
}
