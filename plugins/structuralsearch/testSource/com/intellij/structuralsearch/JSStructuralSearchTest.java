package com.intellij.structuralsearch;

import com.intellij.lang.javascript.JavaScriptSupportLoader;

/**
 * @author Eugene.Kudelevsky
 */
public class JSStructuralSearchTest extends StructuralSearchTestCase {

  public void test1() {
    String s = "location.host.indexOf(\"name\");\n" +
               "host.indexOf(\"name\") ;\n" +
               "object.indexOf( \"text\" );\n";
    doTest(s, "location.host.$method$($arg$) ;", 1);
    doTest(s, "$var$.indexOf($arg$);\n$var1$.indexOf($arg1$);", 1);
    doTest(s, "host.indexOf( \"name\" )", 1);
    doTest(s, "host.indexOf(\"name\");", 1);
    doTest(s, "location.$var$.indexOf( $arg$ )", 1);
    doTest(s, "$var$.indexOf($arg$);$var1$.indexOf($arg$);", 1);
  }

  public void test2() {
    String s = "location.host.indexOf(\"name\");\n" +
               "host.indexOf(\"name\");\n" +
               "object.indexOf(\"text\");\n";
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
    doTest(s, "$var$.func( $value$ )", 2);
    doTest(s, "$var$.func($var$)", 1);
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
               "var b = 10;";
    doTest(s, "var a = 10", 1);
  }

  public void testInnerExpression1() {
    String s = "a + b + c";

    options.setRecursiveSearch(true);
    doTest(s, "$var1$ + $var2$", 2);
    options.setRecursiveSearch(false);
    doTest(s, "$var1$ + $var2$", 1);

    doTest(s, "a+b", 1);
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
    doTest(s, "if ($condition$) {\n" +
              "  $exp$;" +
              "}", 0);
    doTest(s, "if ($condition$) $exp$", 0);
    doTest(s, "if ($condition$)", 1);
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
    doTest(s, "for (var $var$ = $start$; $var$ < $end$; $var$++)\n" +
              "  $exp$;", 1);
    doTest(s, "for (var $var$ = $start$; $var$ < $end$; $var$++) {\n" +
              "}", 0);
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
    doTest(s, "while( $var$ < $end$) $exp$;", 0);
    doTest(s, "for each(var $var$ in $list$)\n" +
              "  $exp$;", 1);
    doTest(s, "for (var $var$ = $start$; $var$ < $end$; $var$++)", 1);
  }

  public void testFunc1() {
    String s = "function f1() {}\n" +
               "function f2() {}\n";
    doTest(s, "function $name$() {}", 2);
    doTest(s, "function f1() {}", 1);
  }

  public void testFunc2() {
    String s = "function f1() {}\n" +
               "function f2() {}\n";
    doTest(s, "function f1()", 1);
    doTest(s, "function $name$()", 2);
  }

  public void testFunc3() {
    String s = "function f1() {}\n" +
               "function f2() {\n" +
               "  object.someMethod();\n" +
               "}\n";
    doTest(s, "function $name$()", 2);
  }

  public void testFunc4() {
    String s = "function f1() {}\n" +
               "function f2() {\n" +
               "  object.someMethod();\n" +
               "}\n";
    doTest(s, "function $name$() {}", 1);
  }

  public void testParams() {
    String s = "function sum(a, b) {}\n" +
               "function f(a, c) {}\n" +
               "function g(b, c) {}\n" +
               "function func(a) {}";
    doTest(s, "function sum($param1$, $param2$) {}", 1);
    doTest(s, "function $name$($param1$, $param2$) {}", 3);
    doTest(s, "function $name$(a, $param2$) {}", 2);
    doTest(s, "function $name$($param1$, c) {}", 2);
    doTest(s, "function '_T('_T1*) {}", 4);
  }

  public void doTest(String source, String pattern, int expectedOccurences) {
    assertEquals(expectedOccurences, findMatches(source, pattern, true, JavaScriptSupportLoader.JAVASCRIPT).size());
  }
}
