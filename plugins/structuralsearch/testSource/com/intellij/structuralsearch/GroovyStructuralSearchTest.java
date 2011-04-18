package com.intellij.structuralsearch;

import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author Eugene.Kudelevsky
 */
public class GroovyStructuralSearchTest extends StructuralSearchTestCase {

  public void test1() throws Exception {
    String s = "def int x = 0;\n" +
               "def y = 0;\n" +
               "int z = 10;\n" +
               "def int x1";

    doTest(s, "def $x$", 4);
    doTest(s, "int $x$", 3);
    doTest(s, "def $x$ = $value$", 3);
    doTest(s, "def $x$ = 0", 2);
    doTest(s, "int $x$ = 0", 1);
    doTest(s, "int $x$ = $value$", 2);
    doTest(s, "def $x$ = $value$;", 3);
  }

  public void test2() throws Exception {
    String s = "def void f(int x) {}\n" +
               "def f(int x) {\n" +
               "  System.out.println(\"hello\");\n" +
               "}\n" +
               "def f(def x) {}\n" +
               "void g(x) {}\n" +
               "public def void f(def int y) {\n" +
               "  System.out.println(\"hello\");\n" +
               "}\n" +
               "def int f() {}";

    doTest(s, "def $f$($param$)", 5);
    doTest(s, "def $f$($param$) {}", 3);
    doTest(s, "void $f$($param$) {}", 2);
    doTest(s, "void $f$(def x)", 2);
    doTest(s, "def $f$(def x)", 4);
    doTest(s, "void $f$(def $x$)", 3);
    doTest(s, "void $f$(int $x$)", 2);
    doTest(s, "def $f$(int $x$)", 3);
    doTest(s, "def g($param$)", 1);
    doTest(s, "def '_T1('_T2*)", 6);
    doTest(s, "def '_T1('_T2*) {'_T3*}", 6);
    doTest(s, "def '_T1('_T2*) {'_T3+}", 2);
  }

  private void doTest(String source,
                      String pattern,
                      int expectedOccurences) {
    assertEquals(expectedOccurences,
                 findMatches(source, pattern, true, GroovyFileType.GROOVY_FILE_TYPE, null, GroovyFileType.GROOVY_FILE_TYPE, null, false)
                   .size());
  }
}
