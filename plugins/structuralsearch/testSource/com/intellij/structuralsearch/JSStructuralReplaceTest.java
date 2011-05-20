package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.io.FileUtil;

import java.io.IOException;

import static com.intellij.lang.javascript.JavaScriptSupportLoader.JAVASCRIPT;

/**
 * @author Eugene.Kudelevsky
 */
public class JSStructuralReplaceTest extends StructuralReplaceTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    options.getMatchOptions().setFileType(JAVASCRIPT);
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

    doTest(s, what2, by2, expected2);

    String what1 = "doc.print($var$);";
    String by1 = "doc.write($var$);";
    String expected1 = "doc.write(\"1\");\n" +
                       "doc.write(\"2\");";

    doTest(s, what1, by1, expected1);
  }

  /*public void test2() {
    String s = "var a = \"hello\";\n" +
               "var b = \"hello\";";

    String what = "var $var$ = \"hello\"";
    String by = "var $var$ = \"bye\"";
    String expected = "var a = \"bye\";\n" +
                      "var b = \"bye\";";

    doTest(s, what, by, expected);
  }*/

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

    doTest(s, what, by, expected, false);
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
    doTest(s, what1, by1, expected1);

    options.setToReformatAccordingToStyle(true);
    String what2 = "f('T*);";
    String by2 = "f($T$, null);";
    String expected2 = "f(1, 2, null);\n" +
                       "f(1, null);\n" +
                       "f(null);";
    doTest(s, what2, by2, expected2);
  }

  public void testUnsupportedPatterns() {
    String s = "someCode()";
    try {
      replacer.testReplace(s, "123", "", options, false, true, JAVASCRIPT, null);
      fail();
    }
    catch (UnsupportedPatternException e) {
    }
    try {
      replacer.testReplace(s, "doc.method()", "", options, false, true, JAVASCRIPT, null);
      fail();
    }
    catch (UnsupportedPatternException e) {
    }
    try {
      replacer.testReplace(s, "doc.method()", "", options, false, true, JAVASCRIPT, JavaScriptSupportLoader.ECMA_SCRIPT_L4);
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

    doTest(s, what1, by1, expected1);

    String what2 = "if ($var$ == 0) $exp1$; else $exp2$;";
    String by2 = "if ($var$ == 1) $exp1$; else $exp2$;";
    String expected2 = "function f() {\n" +
                       "  doc.init();\n" +
                       "  if (a == 1) doc.print(\"zero\"); else doc.print(\"not zero\");\n" +
                       "}";

    doTest(s, what2, by2, expected2);

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

    doTest(s, what3, by3, expected3);
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
    doTest(s, what1, by1, expected1);
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
    doTest(s, what1, by1, expected1);
  }

  public void testInHtml() throws IOException {
    //doTestByFile("script.html", "script_replacement1.html", "var $i$ = $val$", "var $i$: int = $val$", JAVASCRIPT, "js");
    try {
      doTestByFile("script.html", "script.html", "$i$ < $n$", "$i$ > $n$", JAVASCRIPT, null);
      fail();
    }
    catch (UnsupportedPatternException e) {
    }
    doTestByFile("script.html", "script_replacement2.html", "for (var $i$ = 0; $i$ < $n$; $i$++) {}",
                 "for (var $i$ = $n$ - 1; $i$ >= 0; $i$--) {}",
                 JAVASCRIPT, null);
    doTestByFile("script.html", "script_replacement3.html", "$func$();", ";", JAVASCRIPT, null);

    try {
      doTestByFile("script.html", "script_replacement1.html", "$func$()", ";", JAVASCRIPT, null);
      fail();
    }
    catch (UnsupportedPatternException e) {
    }
  }

  /*public void testInMxml() throws IOException {
    doTestByFile("script.mxml", "script_replacement1.mxml", "var $i$ = $val$", "var $i$:int = $val$", JAVASCRIPT, "as");
    doTestByFile("script.mxml", "script.mxml", "var $i$ = $val$", "var $i$:int = $val$", JAVASCRIPT, "js");
    doTestByFile("script.mxml", "script_replacement2.mxml", "$func$();", "func();", JAVASCRIPT, "as");
    try {
      doTestByFile("script.mxml", "script.mxml", "function f(n:int)", "function g(n:int)", JAVASCRIPT, "as");
      fail();
    }
    catch (UnsupportedPatternException e) {
    }
  }*/

  private void doTestByFile(String fileName,
                            String expectedFileName,
                            String what,
                            String by,
                            FileType patternFileType,
                            Language patternFileDialect) throws IOException {
    String extension = FileUtil.getExtension(fileName);
    String source = TestUtils.loadFile(fileName);
    String expected = TestUtils.loadFile(expectedFileName);
    doTest(source, what, by, expected, patternFileType, patternFileDialect, FileTypeManager.getInstance().getFileTypeByExtension(extension),
           null);
  }

  private void doTest(String s, String what, String by, String expected) {
    doTest(s, what, by, expected, true);
  }

  private void doTest(String s, String what, String by, String expected, boolean wrapAsSourceWithFunction) {
    doTest(s, what, by, expected, JAVASCRIPT, null, JAVASCRIPT, null);

    if (wrapAsSourceWithFunction) {
      s = "class A { function f() {" + s + "} }";
      expected = "class A { function f() {" + expected + "} }";
    }
    doTest(s, what, by, expected, JAVASCRIPT, JavaScriptSupportLoader.ECMA_SCRIPT_L4, JAVASCRIPT, JavaScriptSupportLoader.ECMA_SCRIPT_L4);
  }

  private static String removeAllSpaces(String s) {
    final StringBuilder builder = new StringBuilder();

    for (int i = 0, n = s.length(); i < n; i++) {
      final char c = s.charAt(i);
      if (c != '\n' &&
          (c != ' ' ||
           (i > 0 && i < n - 1 &&
            Character.isLetterOrDigit(s.charAt(i - 1)) &&
            Character.isLetterOrDigit(s.charAt(i + 1))))) {
        builder.append(c);
      }
    }
    return builder.toString();
  }

  private void doTest(String s,
                      String what,
                      String by,
                      String expected,
                      FileType patternFileType,
                      Language patternDialect,
                      FileType sourceFileType,
                      Language sourceDialect) {
    options.getMatchOptions().setFileType(patternFileType);
    options.getMatchOptions().setDialect(patternDialect);
    actualResult = replacer.testReplace(s, what, by, options, true, true, sourceFileType, sourceDialect);

    assertEquals(removeAllSpaces(expected), removeAllSpaces(actualResult));
  }
}
