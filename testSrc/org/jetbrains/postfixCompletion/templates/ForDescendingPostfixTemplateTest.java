package org.jetbrains.postfixCompletion.templates;

public class ForDescendingPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testIntArray() {
    doTest();
  }

  public void testByteNumber() {
    doTest();
  }

  public void testBoxedIntegerArray() {
    doTest();
  }

  public void testBoxedLongArray() {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return "testData/templates/forr";
  }
}
