package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class NotExpressionPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "plugins/postfix/testData/templates/not"; }

  public void testSimple()            { doTest(); }
  public void testComplexCondition()  { doTest(); }
  public void testBoxedBoolean()  { doTest(); }
//  public void testNegation()          { doTest(); } // todo: test for chooser 
}