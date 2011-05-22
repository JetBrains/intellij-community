package com.intellij.structuralsearch;

import com.intellij.structuralsearch.extenders.PhpStructuralSearchProfile;
import com.intellij.structuralsearch.impl.matcher.MatcherImpl;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.jetbrains.php.lang.PhpFileType;

/**
 * @author Eugene.Kudelevsky
 */
public class PhpStructuralSearchTest extends StructuralSearchTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    options.setPatternContext(PhpStructuralSearchProfile.FILE_CONTEXT);
  }

  public void test1() throws Exception {
    String s = "<?php\n" +
               "if ($exp) {\n" +
               "  print b;\n" +
               "}";
    doTest(s, "if ($exp$) {\n" +
              "  $st$\n" +
              "}", 1);
    doTest(s, "if ($exp$) {\n" +
              "  $st$;\n" +
              "}", 1);
    doTest(s, "if ($exp$)", 1);
    doTest(s, "if ($exp$) {}", 0);
  }

  public void test2() throws Exception {
    String s = "<?php\n" +
               "$this->_buckets = $bu;";
    doTest(s, "$a$->_buckets = $c$;", 1);
    doTest(s, "$a$->_buckets = $bu;", 1);
    doTest(s, "$a$->$b$ = $c$;", 1);
    doTest(s, "$a$->_buckets = $bu1;", 0);

    doTest(s, "$this->_buckets = $bu;", 1);
    doTest(s, "$this->_buckets = $c$;", 1);
    doTest(s, "$this->$b$ = $c$;", 1);
    doTest(s, "$this->_buckets = $bu1;", 0);
  }

  public void test3() throws Exception {
    String s = "<?php\n" +
               "function f($param) {\n" +
               "  echo \"hello\";\n" +
               "}\n" +
               "function f($param) {\n" +
               "  echo \"123\";\n" +
               "}\n" +
               "function g($param) {\n" +
               "  echo $param;\n" +
               "}";

    doTest(s, "function f($param$) {\n" +
              "  echo \"hello\";\n" +
              "}", 1);
    doTest(s, "function $f$($param$) {\n" +
              "  echo \"hello\";\n" +
              "}", 1);
    doTest(s, "function $f$($param$) {\n" +
              "  echo $s$;\n" +
              "}", 3);
    doTest(s, "function f($param$) {\n" +
              "  echo $s$;\n" +
              "}", 2);
    doTest(s, "function $f$($param$) {\n" +
              "  echo $param$;\n" +
              "}", 1);
    doTest(s, "function g($param$) {\n" +
              "  echo $param$;\n" +
              "}", 1);
    doTest(s, "function f($param$)", 2);
    doTest(s, "function $f$($param$)", 3);
  }

  public void test4() throws Exception {
    String s = "<?php\n" +
               "if ($a == 1) {\n" +
               "  echo \"yes\";\n" +
               "}\n" +
               "else {\n" +
               "  echo \"no\";\n" +
               "}";
    doTest(s, "if ($cond$)", 1);
    doTest(s, "if ($var$ == $value$)", 1);
    doTest(s, "if ($var$ == 2)", 0);
  }

  public void test5() throws Exception {
    String s = "<?php\n" +
               "function f($a, $b) {}";
    doTest(s, "function $f$('_T+) {}", 1);
    doTest(s, "function $f$('_T*)", 1);

    String pattern = "function $f$($param$)";
    options.clearVariableConstraints();
    options.setSearchPattern(pattern);
    MatcherImplUtil.transform(options);
    pattern = options.getSearchPattern();
    options.setFileType(PhpFileType.INSTANCE);
    options.setDialect(null);

    final MatchVariableConstraint constraint = new MatchVariableConstraint();
    constraint.setName("param");
    options.addVariableConstraint(constraint);

    constraint.setMinCount(2);
    MatcherImpl.validate(myProject, options);
    assertEquals(1, testMatcher.testFindMatches(s, pattern, options, true, PhpFileType.INSTANCE, null, false).size());

    constraint.setMinCount(3);
    MatcherImpl.validate(myProject, options);
    assertEquals(0, testMatcher.testFindMatches(s, pattern, options, true, PhpFileType.INSTANCE, null, false).size());

    // todo: fix it. currently user get unexpected matching $param$ with parameter_list node.
    /*constraint.setMinCount(1);
    constraint.setMaxCount(1);
    MatcherImpl.validate(myProject, options);
    assertEquals(0, testMatcher.testFindMatches(s, pattern, options, true, PhpFileType.INSTANCE, null, false).size());*/
  }

  public void test6() throws Exception {
    String s = "<?php\n" +
               "if ($a == 1) {\n" +
               "  echo \"aba\";" +
               "  echo \"caba\";" +
               "}\n" +
               "if ($b == 1) {\n" +
               "  echo \"aba\";\n" +
               "}\n" +
               "if ($c == 1) {}";
    doTest(s, "if ($cond$) {\n" +
              " '_T*" +
              "}", 3);
    doTest(s, "if ($cond$) {\n" +
              " '_T+" +
              "}", 2);
    doTest(s, "if ($cond$) {\n" +
              " $st$" +
              "}", 1);


    // todo: provide equivalence decriptor for php
    /*doTest(s, "if ($cond$) {\n" +
              " '_T+;" +
              "}", 2);
    doTest(s, "if ($cond$) {\n" +
              " '_T*;" +
              "}", 3);
    doTest(s, "if ($cond$) {\n" +
              " $st$;" +
              "}", 1);*/
  }

  public void testMethod() throws Exception {
    String s = "<?php\n" +
               "class C {\n" +
               "  public function f($param1, $param2) {\n" +
               "    print 'Hello';\n" +
               "  }\n" +
               "  public function g() {}" +
               "}";
    options.setPatternContext(PhpStructuralSearchProfile.CLASS_CONTEXT);
    doTest(s, "public function '_T('_T1*) {'_T2*}", 2);
    doTest(s, "public function g() {}", 1);
  }

  private void doTest(String source,
                      String pattern,
                      int expectedOccurences) {
    assertEquals(expectedOccurences,
                 findMatches(source, pattern, true, PhpFileType.INSTANCE, null, PhpFileType.INSTANCE, null, false)
                   .size());
  }
}