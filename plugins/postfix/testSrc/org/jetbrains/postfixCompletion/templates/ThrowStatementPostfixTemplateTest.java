package org.jetbrains.postfixCompletion.templates;

public class ThrowStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "plugins/postfix/testData/templates/throw"; }

  public void testSimple() { doTest(); }
}
