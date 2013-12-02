package org.jetbrains.postfixCompletion.templates;

public class WhileStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testBooleanVariable() throws Exception {
    doTest();
  }

  public void testStringVariable() throws Exception {
    doTest();
  }

  public void testUnresolvedVariable() throws Exception {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return "testData/templates/while";
  }
}
