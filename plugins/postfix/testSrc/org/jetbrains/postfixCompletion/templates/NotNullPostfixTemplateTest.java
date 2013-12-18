package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class NotNullPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "testData/templates/notnull"; }

  public void testSimple()            { doTest(); }
  public void testNn()                { doTest(); }
  public void testSecondStatement()   { doTest(); }
}