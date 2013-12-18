package org.jetbrains.postfixCompletion.templates;

/**
 * @author ignatov
 */
public class SwitchPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected String getTestDataPath() { return "testData/templates/switch"; }

  public void testInt()       { doTest(); }
  public void testByte()      { doTest(); }
  public void testChar()      { doTest(); }
  public void testShort()     { doTest(); }
  public void testEnum()      { doTest(); }
  public void testString()    { doTest(); }
  public void testComposite() { doTest(); }
}