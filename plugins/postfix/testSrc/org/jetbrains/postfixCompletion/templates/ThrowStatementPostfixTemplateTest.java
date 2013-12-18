package org.jetbrains.postfixCompletion.templates;

public class ThrowStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "testData/templates/throw"; }

  public void testSimple() { doTest(); }
}
