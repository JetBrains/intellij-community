package com.intellij.structuralsearch;

import com.intellij.lang.javascript.JavaScriptSupportLoader;

/**
 * @author Eugene.Kudelevsky
 */
public class JSStructuralReplaceTest extends StructuralReplaceTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    options.getMatchOptions().setFileType(JavaScriptSupportLoader.JAVASCRIPT);
  }

  public void test0() {
  }

  public void test1() {
    String s = "doc.print(\"1\");\n" +
               "doc.print(\"2\");";

    String what2 = "doc.print(\"1\")";
    String by2 = "doc.write(\"1\")";
    String expected2 = "doc.write(\"1\");\n" +
                       "doc.print(\"2\");";

    actualResult = replacer.testReplace(s, what2, by2, options, false, true);
    assertEquals(expected2, actualResult);

    String what1 = "doc.print($var$)";
    String by1 = "doc.write($var$)";
    String expected1 = "doc.write(\"1\");\n" +
                       "doc.write(\"2\");";

    actualResult = replacer.testReplace(s, what1, by1, options, false, true);
    assertEquals(expected1, actualResult);
  }

  public void test2() {
    String s = "var a = \"hello\";\n" +
               "var b = \"hello\";";

    String what = "var $var$ = \"hello\"";
    String by = "var $var$ = \"bye\"";
    String expected = "var a = \"bye\";\n" +
                      "var b = \"bye\";";

    actualResult = replacer.testReplace(s, what, by, options, false, true);
    assertEquals(expected, actualResult);
  }

  /*public void test3() {
    String s = "function f() {\n" +
               "    document.appendChild(null, \"hello\");\n" +
               "    document.appendChild(\"ehlo\", \"ehlo\");\n" +
               "}";

    String what = "document.appendChild($arg1$,$arg2$);";
    String by = "document.appendChild();";
    String expected = "function f() {\n" +
                      "    document.appendChild();\n" +
                      "    document.appendChild();\n" +
                      "}";

    actualResult = replacer.testReplace(s, what, by, options, false, true);
    assertEquals(expected, actualResult);
  }*/
}
