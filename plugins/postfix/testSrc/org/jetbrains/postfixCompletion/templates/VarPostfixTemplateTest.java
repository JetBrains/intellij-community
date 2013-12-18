package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class VarPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "testData/templates/var"; }

  public void testSimple() { doTest(); }
  public void testAdd()  { doTest(); }
}
