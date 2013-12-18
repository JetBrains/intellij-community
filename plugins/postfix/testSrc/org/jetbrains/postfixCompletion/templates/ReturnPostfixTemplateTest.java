package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class ReturnPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "testData/templates/return"; }

  public void testSimple()     { doTest(); }
  public void testComposite()  { doTest(); }
  public void testComposite2() { doTest(); }
}