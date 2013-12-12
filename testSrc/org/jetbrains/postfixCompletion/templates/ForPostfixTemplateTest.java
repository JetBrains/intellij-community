package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class ForPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "testData/templates/for"; }

  public void testInts() { doTest(); }
}
