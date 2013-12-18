package org.jetbrains.postfixCompletion.templates;

public class ForeachTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "testData/templates/for"; }

  public void testInts() { doTest(); }
}
