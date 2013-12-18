package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class SynchronizedPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "plugins/postfix/testData/templates/synchronized"; }

  public void testObject() { doTest(); }
}