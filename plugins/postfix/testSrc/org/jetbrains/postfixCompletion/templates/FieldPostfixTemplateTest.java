package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class FieldPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "testData/templates/field"; }

  public void testSimple() { doTest(); }
  public void testFoo()    { doTest(); }
}
