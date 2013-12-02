package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class CastPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "testData/templates/cast"; }

  public void testSingleExpression() throws Exception { doTest(); } // jdk mock needed
  public void testVoidExpression() throws Exception { doTest(); }
}
