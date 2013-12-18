package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class ParPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "plugins/postfix/testData/templates/par"; }

  public void testSimple() { doTest(); }
  public void testExtra()  { doTest(); }
}
