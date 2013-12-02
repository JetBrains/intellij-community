package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class InstanceofPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "testData/templates/instanceof"; }

  public void testSingleExpression() throws Exception { doTest(); }
}
