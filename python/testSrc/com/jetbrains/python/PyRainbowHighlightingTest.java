// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyRainbowHighlightingTest extends PyTestCase {

  // positive tests

  public void testParameters() {
    doTest("def foo(<rainbow color='ff000001'>p1</rainbow>, <rainbow color='ff000002'>p2</rainbow>):\n" +
           "    print <rainbow color='ff000001'>p1</rainbow>\n" +
           "    print <rainbow color='ff000002'>p2</rainbow>");
  }

  public void testParameterReassignmentDoesntChangeColors() {
    doTest("def foo(<rainbow color='ff000001'>p</rainbow>):\n" +
           "    print <rainbow color='ff000001'>p</rainbow>\n" +
           "    <rainbow color='ff000001'>p</rainbow> = \"2\"\n" +
           "    print <rainbow color='ff000001'>p</rainbow>");
  }

  public void testPositionalAndKeywordParameters() {
    doTest("def foo(*<rainbow color='ff000003'>args</rainbow>, **<rainbow color='ff000004'>kwargs</rainbow>):\n" +
           "    print <rainbow color='ff000003'>args</rainbow>\n" +
           "    print <rainbow color='ff000004'>kwargs</rainbow>\n");
  }

  public void testLocalVariables() {
    doTest("def foo():\n" +
           "    <rainbow color='ff000001'>p1</rainbow> = \"1\"\n" +
           "    <rainbow color='ff000002'>p2</rainbow> = \"2\"\n" +
           "    print <rainbow color='ff000001'>p1</rainbow>\n" +
           "    print <rainbow color='ff000002'>p2</rainbow>");
  }

  public void testLocalVariableReassignmentDoesntChangeColors() {
    doTest("def foo():\n" +
           "    <rainbow color='ff000001'>t</rainbow> = \"1\"\n" +
           "    print <rainbow color='ff000001'>t</rainbow>\n" +
           "    <rainbow color='ff000001'>t</rainbow> = \"2\"\n" +
           "    print <rainbow color='ff000001'>t</rainbow>");
  }

  public void testTopLevelVariable() {
    doTest("<rainbow color='ff000003'>a</rainbow> = 1\n" +
           "def foo():\n" +
           "    print <rainbow color='ff000003'>a</rainbow>");
  }

  public void testTopLevelVariableReassignmentDoesntChangeColors() {
    doTest("<rainbow color='ff000003'>a</rainbow> = 10\n" +
           "print <rainbow color='ff000003'>a</rainbow>\n" +
           "<rainbow color='ff000003'>a</rainbow> = 11\n" +
           "print <rainbow color='ff000003'>a</rainbow>");
  }

  public void testLocalLambdaParametersHaveOwnColors() {
    doTest("def foo():\n" +
           "    <rainbow color='ff000001'>l</rainbow> = 5\n" +
           "    <rainbow color='ff000002'>m</rainbow> = 5\n" +
           "    <rainbow color='ff000003'>n1</rainbow> = 10\n" +
           "    <rainbow color='ff000004'>n2</rainbow> = 10\n" +
           "    map(lambda <rainbow color='ff000002'>n1</rainbow>, <rainbow color='ff000001'>n2</rainbow>: <rainbow color='ff000002'>n1</rainbow> * <rainbow color='ff000001'>n2</rainbow>, zip(range(10), range(10)))");
  }

  public void testTopLevelLambdaParametersHaveOwnColors() {
    doTest("<rainbow color='ff000004'>l</rainbow> = 5\n" +
           "<rainbow color='ff000001'>m</rainbow> = 5\n" +
           "<rainbow color='ff000002'>n1</rainbow> = 10\n" +
           "<rainbow color='ff000003'>n2</rainbow> = 10\n" +
           "map(lambda <rainbow color='ff000002'>n1</rainbow>, <rainbow color='ff000001'>n2</rainbow>: <rainbow color='ff000002'>n1</rainbow> * <rainbow color='ff000001'>n2</rainbow>, zip(range(10), range(10)))");
  }

  public void testSameNameLocalAndTopLevelVariableHaveDifferentColors() {
    doTest("<rainbow color='ff000004'>l</rainbow> = 5\n" +
           "<rainbow color='ff000001'>g</rainbow> = 10\n" +
           "def foo():\n" +
           "    <rainbow color='ff000004'>g</rainbow> = 20");
  }

  public void testSameNameLocalAndGlobalVariableHaveSameColors() {
    doTest("<rainbow color='ff000004'>l</rainbow> = 5\n" +
           "def foo():\n" +
           "    global <rainbow color='ff000001'>g</rainbow>\n" +
           "    <rainbow color='ff000001'>g</rainbow> = 20");
  }

  public void testSameNameOuterAndNonLocalVariableAndItsReferenceHaveSameColors() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON30,
      () -> doTest("def outer():\n" +
                   "    <rainbow color='ff000001'>another</rainbow> = None\n" +
                   "    <rainbow color='ff000002'>another2</rainbow> = None\n" +
                   "    <rainbow color='ff000003'>another3</rainbow> = None\n" +
                   "    <rainbow color='ff000004'>from_outer</rainbow> = 1\n" +
                   "    def nested():\n" +
                   "        nonlocal <rainbow color='ff000004'>from_outer</rainbow>\n" +
                   "        print(<rainbow color='ff000004'>from_outer</rainbow>)\n")
    );
  }

  public void testSameNameOuterAndNonLocalVariableAndItsReferenceAfterReassignmentHaveSameColors() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON30,
      () -> doTest("def outer():\n" +
                   "    <rainbow color='ff000001'>another</rainbow> = None\n" +
                   "    <rainbow color='ff000002'>another2</rainbow> = None\n" +
                   "    <rainbow color='ff000003'>another3</rainbow> = None\n" +
                   "    <rainbow color='ff000004'>from_outer</rainbow> = 1\n" +
                   "    def nested():\n" +
                   "        nonlocal <rainbow color='ff000004'>from_outer</rainbow>\n" +
                   "        <rainbow color='ff000004'>from_outer</rainbow> = 2\n" +
                   "        print(<rainbow color='ff000004'>from_outer</rainbow>)\n")
    );
  }

  public void testFakeSelfParameter() {
    doTest("def foo(<rainbow color='ff000001'>self</rainbow>):\n" +
           "    print <rainbow color='ff000001'>self</rainbow>");
  }

  public void testVariableAfterAugAssignment() {
    doTest("<rainbow color='ff000002'>index</rainbow> = 1\n" +
           "<rainbow color='ff000002'>index</rainbow> += 1\n" +
           "print(<rainbow color='ff000002'>index</rainbow>)");
  }

  public void testVariableInsideLambda() {
    doTest("<rainbow color='ff000004'>l</rainbow> = lambda <rainbow color='ff000004'>x</rainbow>: sum(<rainbow color='ff000002'>y</rainbow> * <rainbow color='ff000002'>y</rainbow> for <rainbow color='ff000002'>y</rainbow> in <rainbow color='ff000004'>x</rainbow>)");
  }

  // EA-96587
  public void testEa96587() {
    doTest("<rainbow color='ff000003'>a</rainbow> = 10\n" +
           "<rainbow color='ff000003'>a</rainbow> += 10\n" +
           "for <rainbow color='ff000004'>i</rainbow> in range(10):\n" +
           "    <rainbow color='ff000003'>a</rainbow> += 10\n" +
           "    <rainbow color='ff000003'>a</rainbow> += 10");
  }

  // negative tests

  public void testSelfParameter() {
    doTest("class A:\n" +
           "    def foo(self):\n" +
           "        print self");
  }

  public void testAttributesAsReferences() {
    doTest("class A:\n" +
           "    def foo(self, <rainbow color='ff000001'>p</rainbow>):\n" +
           "        print self.q\n" +
           "        print <rainbow color='ff000001'>p</rainbow>.q");
  }

  public void testClassLevelAttributeAsReference() {
    doTest("class A:\n" +
           "    var_a = 10\n" +
           "    def foo(self):\n" +
           "        print self.var_a");
  }

  public void testAttributesAsTargets() {
    doTest("class A:\n" +
           "    def foo(self, <rainbow color='ff000001'>p</rainbow>):\n" +
           "        self.q = 10\n" +
           "        <rainbow color='ff000001'>p</rainbow>.q = 10");
  }

  public void testClassLevelAttributeAsTarget() {
    doTest("class A:\n" +
           "    var_a = 10\n" +
           "    def foo(self):\n" +
           "        self.var_a = 10");
  }

  public void testClassAsCallee() {
    doTest("class A:\n" +
           "    def foo(self):\n" +
           "        print A()");
  }

  public void testFunctionAsCallee() {
    doTest("def foo():\n" +
           "    pass\n" +
           "def bar():\n" +
           "    print foo()");
  }

  public void testClassAsVariable() {
    doTest("class A:\n" +
           "    def foo(self):\n" +
           "        print A");
  }

  public void testFunctionAsVariable() {
    doTest("def foo():\n" +
           "    pass\n" +
           "def bar():\n" +
           "    print foo");
  }

  public void testNoneAsReference() {
    doTest("<rainbow color='ff000001'>v</rainbow> = None");
  }

  public void testNoneAsTarget() {
    doTest("None = object()");
  }

  public void testTrueAsReference() {
    doTest("<rainbow color='ff000001'>v</rainbow> = True");
  }

  public void testTrueAsTarget() {
    doTest("True = object()");
  }

  public void testFalseAsReference() {
    doTest("<rainbow color='ff000001'>v</rainbow> = False");
  }

  public void testFalseAsTarget() {
    doTest("False = object()");
  }

  public void testParameterDefaultValue() {
    doTest("def foo(<rainbow color='ff000001'>p1</rainbow>=1):\n" +
           "    pass");
  }

  public void testParameterTypeHint() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("def foo(<rainbow color='ff000001'>p1</rainbow>: int):\n" +
                   "    pass")
    );
  }

  private void doTest(@NotNull String text) {
    myFixture.testRainbow(getTestName(true) + ".py", text, true, true);
  }
}
