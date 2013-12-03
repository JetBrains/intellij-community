package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class ParPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "testData/templates/par"; }

  public void testSimple() throws Exception { doTest(); }
}
