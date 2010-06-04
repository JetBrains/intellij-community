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

    String what2 = "doc.print(\"1\");";
    String by2 = "doc.write(\"1\");";
    String expected2 = "doc.write(\"1\");\n" +
                       "doc.print(\"2\");";

    actualResult = replacer.testReplace(s, what2, by2, options, false, true);
    assertEquals(expected2, actualResult);

    String what1 = "doc.print($var$);";
    String by1 = "doc.write($var$);";
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

  public void test3() {
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
  }

  public void test4() {
    String s = "f(1, 2);\n" +
               "f(1);\n" +
               "f();";
    String what1 = "f('T*);";
    String by1 = "f();";
    String expected1 = "f();\n" +
                       "f();\n" +
                       "f();";
    actualResult = replacer.testReplace(s, what1, by1, options, false, true);
    assertEquals(expected1, actualResult);

    options.setToReformatAccordingToStyle(true);
    String what2 = "f('T*);";
    String by2 = "f($T$, null);";
    String expected2 = "f(1, 2, null);\n" +
                       "f(1, null);\n" +
                       "f(null);";
    actualResult = replacer.testReplace(s, what2, by2, options, false, true);
    assertEquals(expected2, actualResult);
  }

  public void testUnsupportedPatterns() {
    String s = "someCode()";
    try {
      replacer.testReplace(s, "123", "", options, false, true);
      fail();
    }
    catch (UnsupportedPatternException e) {
    }
    try {
      replacer.testReplace(s, "doc.method()", "", options, false, true);
      fail();
    }
    catch (UnsupportedPatternException e) {
    }
  }

  public void testCondition() {
    String s = "function f() {\n" +
               "  doc.init();\n" +
               "  if (a == 0) {\n" +
               "    doc.print(\"zero\");\n" +
               "  }\n" +
               "  else {\n" +
               "    doc.print(\"not zero\");\n" +
               "  }\n" +
               "}";

    String what1 = "if ($var$ == 0) {$exp1$;} else {$exp2$;}";
    String by1 = "if ($var$ == 1) {$exp1$;} else {$exp2$;}";
    String expected1 = "function f() {\n" +
                       "  doc.init();\n" +
                       "  if (a == 1) {doc.print(\"zero\");} else {doc.print(\"not zero\");}\n" +
                       "}";

    actualResult = replacer.testReplace(s, what1, by1, options, false, true);
    assertEquals(expected1, actualResult);

    String what2 = "if ($var$ == 0) $exp1$; else $exp2$;";
    String by2 = "if ($var$ == 1) $exp1$; else $exp2$;";
    String expected2 = "function f() {\n" +
                       "  doc.init();\n" +
                       "  if (a == 1) doc.print(\"zero\"); else doc.print(\"not zero\");\n" +
                       "}";

    actualResult = replacer.testReplace(s, what2, by2, options, false, true);
    assertEquals(expected2, actualResult);

    String what3 = "if ($var$ == 0)";
    String by3 = "if ($var$ == 1) doc.write(\"hello\");";
    String expected3 = "function f() {\n" +
                       "  doc.init();\n" +
                       "  if (a == 0) {\n" +
                       "    doc.print(\"zero\");\n" +
                       "  }\n" +
                       "  else {\n" +
                       "    doc.print(\"not zero\");\n" +
                       "  }\n" +
                       "}";

    actualResult = replacer.testReplace(s, what3, by3, options, false, true);
    assertEquals(expected3, actualResult);
  }

  public void testLoop() {
    String s = "for (var i = 0; i < n ; i++) {\n" +
               "  doc.print(i);\n" +
               "}";
    String what1 = "for (var $i$ = $start$; $i$ < $end$; $i$++)\n" +
                   "  $exp$;\n";
    String by1 = "var $i$ = $start$;\n" +
                 "while ($i$ < $end$) {\n" +
                 "  $exp$;\n" +
                 "  $i$++;\n" +
                 "}";
    String expected1 = "var i = 0;\n" +
                       "while (i < n) {\n" +
                       "  doc.print(i);\n" +
                       "  i++;\n" +
                       "}";
    actualResult = replacer.testReplace(s, what1, by1, options, false, true);
    assertEquals(expected1, actualResult);
  }

  public void testTryCatchFinally() {
    String s = "try {\n" +
               "  doc.doSomeWork();\n" +
               "}\n" +
               "catch (err) {\n" +
               "  doc.doCatch();" +
               "}\n";

    String what1 = "try {$exp$;} catch(err) {$exp1$;}";
    String by1 = "try {\n" +
                 "$exp$;\n" +
                 "}\n" +
                 "finally {\n" +
                 "$exp1$;\n" +
                 "}";
    String expected1 = "try {\n" +
                       "doc.doSomeWork();\n" +
                       "}\n" +
                       "finally {\n" +
                       "doc.doCatch();\n" +
                       "}\n";
    actualResult = replacer.testReplace(s, what1, by1, options, false, true);
    assertEquals(expected1, actualResult);
  }
}
