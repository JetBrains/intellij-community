package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class NullPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "plugins/postfix/testData/templates/null"; }

  public void testSimple()            { doTest(); }
  public void testSecondStatement()   { doTest(); }
}