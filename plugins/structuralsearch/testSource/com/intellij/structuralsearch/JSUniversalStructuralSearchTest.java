package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.io.FileUtil;

import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class JSUniversalStructuralSearchTest extends StructuralSearchTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    StructuralSearchUtil.ourUseUniversalMatchingAlgorithm = true;

    // todo: test all with recursive search
    options.setRecursiveSearch(false);
  }

  public void test0() {
    String s = "var a = 1;";
    doTest(s, "var $a$ = 1;", 1);
    doTest(s, "var a = 1;", 1);
  }

  public void test1() {
    String s = "location.host.indexOf(\"name\");\n" +
               "host.indexOf(\"name\") ;\n" +
               "object.indexOf( \"text\" );\n";
    doTest(s, "host.indexOf( \"name\" )", 1);
    doTest(s, "$var$.indexOf($arg$);$var1$.indexOf($arg$);", 1);
    doTest(s, "$var$.indexOf($arg$);\n$var1$.indexOf($arg1$);", 1);
    doTest(s, "location.host.$method$($arg$) ;", 1);
    doTest(s, "host.indexOf(\"name\");", 1);
    doTest(s, "location.$var$.indexOf( $arg$ )", 1);
  }

  public void test2() {
    String s = "location.host.indexOf(\"name\");\n" +
               "host.indexOf(\"name\");\n" +
               "obj ect.indexOf(\"text\");\n";
    doTest(s, "$var$.indexOf(\"text\")", 1);
  }

  public void test3() {
    String s = "location.host.indexOf(\"name\");\n" +
               "host.indexOf(\"name\");\n" +
               "object.indexOf(\"text\");\n";
    doTest(s, "$var$.indexOf(\"name\");", 2);
    doTest(s, "$var$.$method$($arg$)", 3);
  }

  public void test4() {
    String s = "host.func(host);\n" +
               "host.func(o);";
    doTest(s, "$var$.func($var$)", 1);
    doTest(s, "$var$.func( $value$ )", 2);
  }

  public void test5() {
    String s = "function f() {\n" +
               "  location.host.indexOf(\"name\");\n" +
               "  host.indexOf(\"name\");object.indexOf( \"text\" );\n" +
               "  object.indexOf( \"text\" );\n" +
               "}\n" +
               "function g() {\n" +
               "  location.host.indexOf(\"name\");\n" +
               "  host.indexOf(\"name\");\n" +
               "}";
    doTest(s, "$var$.$method$($param$);\n$var1$.$method1$($param1$);", 3);
  }

  public void test6() {
    String s = "location.host.indexOf('name');\n" +
               "host.indexOf('name') ;\n" +
               "object.indexOf( \"text\" );\n" +
               "object.indexOf( \"text\" );\n";
    doTest(s, "$var$.indexOf($arg$);\n$var1$.indexOf($arg1$);", 2);
    doTest(s, "$var$.indexOf($arg$);$var$.indexOf($arg1$);", 1);
  }

  public void test7() {
    String s = "a[0] = 1;\n" +
               "b = 2;\n";
    doTest(s, "$var$ = $value$", 2);
    doTest(s, "$var$[0] = $value$", 1);
  }

  public void test8() {
    String s = "var a = 10;\n" +
               "var b = 10;\n";
    doTest(s, "a", 1);
    doTest(s, "var a = 10", 1);
  }

  public void test9() {
    String s = "doc.method1(null, \"a\");\n" +
               "doc.method2(null);\n" +
               "doc.method3(1, 2, 3);\n" +
               "doc.method4();";
    doTest(s, "doc.'_T('_T1+)", 3);
    doTest(s, "doc.'_T('_T1*)", 4);
    doTest(s, "doc.'_T('_T1)", 1);
  }

  public void testInnerExpression1() {
    String s = "a + b + c";

    doTest(s, "$var1$ + $var2$", 1);
    doTest(s, "a+b", 1);

    options.setRecursiveSearch(true);
    doTest(s, "$var1$ + $var2$", 2);
    options.setRecursiveSearch(false);
  }

  public void testInnerExpression2() {
    String s = "((dialog==null)? (dialog = new SearchDialog()): dialog).show();";
    doTest(s, "dialog = new SearchDialog()", 1);
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
    doTest(s, "if ($exp$)" +
              "  doc.print($lit1$);" +
              "else" +
              "  doc.print($lit2$);\n", 1);
    doTest(s, "if ($exp$) {\n" +
              "  doc.print($lit1$);" +
              "} else {" +
              "  doc.print($lit2$);\n" +
              "}", 1);
  }

  public void testCondition2() {
    String s = "function f() {\n" +
               "  if (a == 0) {\n" +
               "    doc.print(\"one\");\n" +
               "    doc.print(\"two\");\n" +
               "  }\n" +
               "  else {\n" +
               "    doc.print(\"not zero\");\n" +
               "  }\n" +
               "}";

    // todo: support this (see canBePatternVariable())
    /*doTest(s, "if ('condition) {\n" +
              "  'exp*;\n" +
              "}", 1);*/

    doTest(s, "if ($condition$) {\n" +
              "  $exp$;" +
              "}", 0);

    doTest(s, "if ($condition$) $exp$", 1);
    doTest(s, "if ($condition$)", 1);

    doTest(s, "if ($condition$) {\n" +
              "  $exp1$;\n" +
              "  $exp2$;\n" +
              "}", 1);
  }


  public void testLoop() {
    String s = "for (var i = 0; i < n ; i++) {\n" +
               "  doc.print(i);\n" +
               "}\n" +
               "for each (var i in list) {\n" +
               "  doc.print(i);\n" +
               "}\n" +
               "var i = 0;\n" +
               "while (i < n) {\n" +
               "  doc.print(i);\n" +
               "  i++;\n" +
               "}";
    doTest(s, "var $i$ = $value$", 2);
    doTest(s, "for (var $var$ = $start$; $var$ < $end$; $var$++)\n" +
              "  $exp$;", 1);
    doTest(s, "for each(var $var$ in $list$){\n" +
              "  $exp$;\n" +
              "}", 1);
    doTest(s, "for (var $var$ = $start$; $var$ < $end$; $var$++) {\n" +
              "  $exp$;\n" +
              "}", 1);
    doTest(s, "for(var $var$ = $start$; $endexp$; $incexp$) {\n" +
              "  $exp$;\n" +
              "}", 1);
    doTest(s, "while( $var$ < $end$) {\n" +
              "  $exp$;\n" +
              "}", 0);
    doTest(s, "while($condition$)", 1);

    // universal matcher can match pattern variable to BLOCK
    doTest(s, "while( $var$ < $end$) $exp$", 1);

    doTest(s, "while( $var$ < $end$) $exp$;", 0);

    doTest(s, "for each(var $var$ in $list$)\n" +
              "  $exp$;", 1);
    doTest(s, "for (var $var$ = $start$; $var$ < $end$; $var$++)", 1);
    doTest(s, "for (var $var$ = $start$; $var$ < $end$; $var$++) {\n" +
              "}", 0);
  }

  public void testFunc1() {
    String s = "function f1() {}\n" +
               "function f2() {}\n";
    doTest(s, "function $name$() {}", 2, false);
    doTest(s, "function f1() {}", 1, false);
  }

  public void testFunc2() {
    String s = "function f1() {}\n" +
               "function f2() {}\n";
    doTest(s, "function f1()", 1, false);
    doTest(s, "function $name$()", 2, false);
  }

  public void testFunc3() {
    String s = "function f1() {}\n" +
               "function f2() {\n" +
               "  object.someMethod();\n" +
               "}\n";
    doTest(s, "function $name$()", 2, false);
  }

  public void testFunc4() {
    String s = "function f1() {}\n" +
               "function f2() {\n" +
               "  object.someMethod();\n" +
               "}\n";
    doTest(s, "function $name$() {}", 1, false);
  }

  public void testParams() {
    String s = "function sum(a, b) {}\n" +
               "function f(a, c) {}\n" +
               "function g(b, c) {}\n" +
               "function func(a) {}";
    doTest(s, "function sum($param1$, $param2$) {}", 1, false);
    doTest(s, "function $name$($param1$, $param2$) {}", 3, false);
    doTest(s, "function $name$(a, $param2$) {}", 2, false);
    doTest(s, "function $name$($param1$, c) {}", 2, false);
    doTest(s, "function '_T('_T1*) {}", 4, false);
  }

  public void testInHtml() throws IOException {
    doTestByFile("script.html", "for (var $i$ = 0; $i$ < n ; $i$++)", 2);
    doTestByFile("script.html", "for (var i = 0; i < n ; i++)", 1);
    doTestByFile("script.html", "$func$();", 2);


    /*doTestByFile("script.html", "<script type=\"text/javascript\">\n" +
                                "   for (var i = 0; i < n; i++) {}\n" +
                                "   for (var j = 0; j < n; j++) {}\n" +
                                "</script>", 1, 1, StdFileTypes.HTML);*/
  }

  public void testInMxml() throws IOException {
    doTestByFile("script.mxml", "var $i$ = $val$", 2, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTestByFile("script.mxml", "for (var i = 0; i < n; i++)", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTestByFile("script.mxml", "for (var $i$ = 0; $i$ < n; $i$++)", 2, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTestByFile("script.mxml", "$func$();", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");

    // todo: test AS in XML attribute values
  }

  public void testAsFunc() throws IOException {
    doTestByFile("class.as", "$a$+$b$", 0);
    doTestByFile("class.as", "function $name$('_param*)", 2, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTestByFile("class.as", "$a$+$b$", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTestByFile("class.as", "public static function sum('_param*)", 0);
    doTestByFile("class.as", "public static function sum('_param*)", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTestByFile("class.as", "function sum('_param*)", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTestByFile("class.as", "private static function sum('_param*)", 0, JavaScriptSupportLoader.JAVASCRIPT, "as");
  }

  public void testAsInterface() throws Exception {
    doTest("interface A { function aba(); }", "aba", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
  }

  public void testStringLiteral() throws Exception {
    String pattern = "\"$str$\"";
    doTest("var s = \"hello\";", pattern, 1);
    doTest("package {\n" +
           "public class MyClass {\n" +
           "    private var s:String = \"hello\";\n" +
           "}\n" +
           "}", pattern, 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest("var s = \"str1\"; var s1 = \"str2\"; var s2 = \"hello\";", "\"'_str:[regex( str.* )]\"", 2);
    doTest("var s = \"hello world\"; var s2 = \"hello\";", "\"$s$ $z$\"", 1);
  }

  public void testClasses() throws Exception {
    String pattern = "class $name$ {}";
    doTest("package {\n" +
           "public class MyClass implements mx.messaging.messages.IMessage {\n" +
           "}\n" +
           "}", pattern, 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest("package {\n" +
           "class MyClass implements mx.messaging.messages.IMessage {\n" +
           "}\n" +
           "}", pattern, 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
  }

  public void testClasses1() throws Exception {
    String c = "package {\n" +
               "public class MyAsClass extends SomeClass {\n" +
               "    function MyAsClass() {}\n" +
               "    function f() {\n" +
               "      var a = 1;" +
               "    }\n" +
               "    function g() {\n" +
               "    }\n" +
               "}\n" +
               "}";
    doTest(c, "class $name$ { function f() }", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest(c, "class $name$ { function g() {} }", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest(c, "class $name$ { function f() {} }", 0, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest(c, "class $name$ { function f() {var a = 1;} }", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest(c, "class $name$ { function g() function f() }", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest(c, "class $name$ { function $name$() }", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
  }

  public void testClasses2() throws Exception {
    String c1 = "package {\n" +
                "class C1 implements I1, I2 {}\n" +
                "}";
    doTest(c1, "class $name$ implements $i1$, $i2$ {}", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest(c1, "class $name$ implements $i1$, $i2$, $i3$ {}", 0, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest(c1, "class $name$ implements I2, I1 {}", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest(c1, "class $name$ implements $i$ {}", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
  }

  public void testStatement2Expression() throws Exception {
    doTest("var s = func();", "func();", 0);
    doTest("var s = func();", "func()", 1);
  }

  public void testParens() {
    doTest("var s = a + b;", "(a + b)", 1);
    doTest("var s = a + b*3;", "a + (b * 3)", 1);
    doTest("var s = a + b*3;", "a + b * 3", 1);
    doTest("var s = a + b*3;", "(a + b) * 3", 0);
  }

  public void testTypedVariable() {
    final String s = "class A {" +
                     "  function f() {" +
                     "    var n: int = 2;" +
                     "  }" +
                     "}";
    doTest(s, "var $n$ = 2", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest(s, "var $n$:$type$ = 2", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest(s, "var $n$", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest(s, "var $n$:$type$", 1, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest(s, "var $n$:float", 0, JavaScriptSupportLoader.JAVASCRIPT, "as");
    doTest(s, "var $n$ = 3", 0, JavaScriptSupportLoader.JAVASCRIPT, "as");
  }

  private void doTestByFile(String fileName, String pattern, int expectedOccurences) throws IOException {
    doTestByFile(fileName, pattern, expectedOccurences, JavaScriptSupportLoader.JAVASCRIPT, "js");
  }

  private void doTestByFile(String fileName,
                            String pattern,
                            int expectedOccurences,
                            FileType patternFileType,
                            String patternFileExtension) throws IOException {
    String extension = FileUtil.getExtension(fileName);
    doTest(TestUtils.loadFile(fileName), pattern, expectedOccurences, patternFileType, patternFileExtension,
           FileTypeManager.getInstance().getFileTypeByExtension(extension), extension, true);
  }

  private void doTest(String source, String pattern, int expectedOccurences) {
    doTest(source, pattern, expectedOccurences, true);
  }

  private void doTest(String source, String pattern, int expectedOccurences, boolean wrapAsSourceWithFunction) {
    doTest(source, pattern, expectedOccurences, JavaScriptSupportLoader.JAVASCRIPT, "js");

    if (wrapAsSourceWithFunction) {
      source = "class A { function f() { " + source + "} }";
    }
    doTest(source, pattern, expectedOccurences, JavaScriptSupportLoader.JAVASCRIPT, "as");
  }

  private void doTest(String source, String pattern, int expectedOccurences, FileType fileType, String extension) {
    doTest(source, pattern, expectedOccurences, fileType, extension, fileType, extension);
  }

  private void doTest(String source,
                      String pattern,
                      int expectedOccurences,
                      FileType patternFileType,
                      String patternFileExtension,
                      FileType sourceFileType,
                      String sourceFileExtension) {
    doTest(source, pattern, expectedOccurences, patternFileType, patternFileExtension, sourceFileType,
           sourceFileExtension, false);
  }

  private void doTest(String source,
                      String pattern,
                      int expectedOccurences,
                      FileType patternFileType,
                      String patternFileExtension,
                      FileType sourceFileType,
                      String sourceFileExtension,
                      boolean physicalSourceFile) {
    Language patternDialect = "as".equals(patternFileExtension) ? JavaScriptSupportLoader.ECMA_SCRIPT_L4 : null;

    assertEquals(expectedOccurences,
                 findMatches(source, pattern, true, patternFileType, patternDialect, sourceFileType, sourceFileExtension,
                             physicalSourceFile).size());
  }
}
