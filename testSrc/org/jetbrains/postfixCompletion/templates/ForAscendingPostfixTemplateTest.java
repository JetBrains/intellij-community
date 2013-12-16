package org.jetbrains.postfixCompletion.templates;

public class ForAscendingPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testIntArray() {
    doTest();
  }

  public void testIntNumber() {
    doTest();
  }

  public void testByteNumber() {
    doTest();
  }

  public void testBoxedByteNumber() {
    doTest();
  }

  public void testCollection() {
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
    return "testData/templates/fori";
  }
}
