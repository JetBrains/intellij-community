package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class NotPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "testData/templates/not"; }

  public void testSimple()            { doTest(); }
  public void testComplexCondition()  { doTest(); }
//  public void testNegation()          { doTest(); } // todo: test for chooser 
}