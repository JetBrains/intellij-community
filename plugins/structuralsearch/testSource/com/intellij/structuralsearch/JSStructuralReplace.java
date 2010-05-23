package com.intellij.structuralsearch;

import com.intellij.lang.javascript.JavaScriptSupportLoader;

/**
 * @author Eugene.Kudelevsky
 */
public class JSStructuralReplace extends StructuralReplaceTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    options.getMatchOptions().setFileType(JavaScriptSupportLoader.JAVASCRIPT);
  }

  public void test0() {
  }

  /*public void test1() {
    String s = "doc.print(\"1\");\n" +
               "doc.print(\"2\");";

    String what2 = "doc.print(\"1\")";
    String by2 = "doc.write(\"1\")";
    String expected2 = "doc.write(\"1\");\n" +
                       "doc.print(\"2\");";

    actualResult = replacer.testReplace(s, what2, by2, options);
    assertEquals(expected2, actualResult);

    String what1 = "doc.print($var$)";
    String by1 = "doc.write($var$)";
    String expected1 = "doc.write(\"1\");\n" +
                       "doc.write(\"2\");";

    actualResult = replacer.testReplace(s, what1, by1, options);
    assertEquals(expected1, actualResult);
  }

  public void test2() {
    String s = "var a = \"hello\";\n" +
               "var b = \"hello\";";

    String what = "var $var$ = \"hello\"";
    String by = "var $var$ = \"bye\"";
    String expected = "var a = \"bye\";\n" +
                      "var b = \"bye\";";

    actualResult = replacer.testReplace(s, what, by, options);
    assertEquals(expected, actualResult);
  }*/
}
