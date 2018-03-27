// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;

/**
 * @author oleg
 */
public class PyControlFlowBuilderTest extends LightMarkedTestCase {

  @Override
  public String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/codeInsight/controlflow/";
  }

  private void doTest() {
    final String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    final ControlFlow flow = ControlFlowCache.getControlFlow((PyFile)myFile);
    final String fullPath = getTestDataPath() + testName + ".txt";
    check(fullPath, flow);
   }

  public void testAssert() {
    doTest();
  }

  public void testAssertFalse() {
    doTest();
  }

  public void testFile() {
    doTest();
  }

  public void testIf() {
    doTest();
  }

  public void testFor() {
    doTest();
  }

  public void testWhile() {
    doTest();
  }

  public void testBreak() {
    doTest();
  }

  public void testContinue() {
    doTest();
  }

  public void testReturn() {
    doTest();
  }

  public void testTry() {
    doTest();
  }

  public void testImport() {
    doTest();
  }

  public void testListComp() {
    doTest();
  }

  public void testAssignment() {
    doTest();
  }

  public void testAssignment2() {
    doTest();
  }

  public void testAugAssignment() {
    doTest();
  }

  public void testSliceAssignment() {
    doTest();
  }

  public void testIfElseReturn() {
    doTest();
  }

  public void testRaise() {
    doTest();
  }

  public void testReturnFor() {
    doTest();
  }

  public void testForIf() {
    doTest();
  }

  public void testForReturn() {
    doTest();
  }

  public void testForTryContinue() {
    doTest();
  }

  public void testTryRaiseFinally() {
    doTest();
  }

  public void testTryExceptElseFinally() {
    doTest();
  }

  public void testTryFinally() {
    doTest();
  }

  public void testDoubleTry() {
    doTest();
  }

  public void testTryTry() {
    doTest();
  }

  public void testIsinstance() {
    doTest();
  }

  public void testLambda() {
    doTest();
  }

  public void testManyIfs() {
    doTest();
  }
  
  public void testSuperclass() {
    doTest();
  }
  
  public void testDefaultParameterValue() {
    doTest();
  }

  public void testLambdaDefaultParameter() {
    doTest();
  }
  
  public void testDecorator() {
    doTestFirstStatement();
  }

  public void testSetComprehension() {
    doTest();
  }
  
  public void testTypeAnnotations() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTest);
  }

  public void testQualifiedSelfReference() {
    final String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    final String fullPath = getTestDataPath() + testName + ".txt";
    final PyClass pyClass = ((PyFile) myFile).getTopLevelClasses().get(0);
    final ControlFlow flow = ControlFlowCache.getControlFlow(pyClass.getMethods()[0]);
    check(fullPath, flow);
  }

  public void testSelf() {
    final String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    final String fullPath = getTestDataPath() + testName + ".txt";
    final PyClass pyClass = ((PyFile) myFile).getTopLevelClasses().get(0);
    final ControlFlow flow = ControlFlowCache.getControlFlow(pyClass.getMethods()[0]);
    check(fullPath, flow);
  }

  public void testTryBreak() {
    final String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    final ControlFlow flow = ControlFlowCache.getControlFlow((PyFunction)((PyFile)myFile).getStatements().get(0));
    final String fullPath = getTestDataPath() + testName + ".txt";
    check(fullPath, flow);
  }

  public void testFunction() {
    doTestFirstStatement();
  }

  // PY-7784
  public void testAssertFalseArgument() {
    doTest();
  }

  public void testConditionalExpression() {
    doTest();
  }

  // PY-20744, PY-20864
  public void testVariableAnnotations() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-21175
  public void testImplicitNegativeTypeAssertionAfterIf() {
    doTest();
  }

  // PY-21175
  public void testImplicitNegativeTypeAssertionAfterTwoNestedIf() {
    doTest();
  }

  // PY-20889
  public void testTypesInAndBooleanExpression() {
    doTest();
  }

  // PY-20889
  public void testTypesInOrBooleanExpression() {
    doTest();
  }

  // PY-25974
  public void testAndBooleanExpression() {
    doTest();
  }

  // PY-25974
  public void testOrBooleanExpression() {
    doTest();
  }

  // PY-14840
  // PY-22003
  public void testPositiveIteration() {
    doTest();
  }

  private void doTestFirstStatement() {
    final String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    final String fullPath = getTestDataPath() + testName + ".txt";
    final ControlFlow flow = ControlFlowCache.getControlFlow((ScopeOwner)((PyFile)myFile).getStatements().get(0));
    check(fullPath, flow);
  }

  private static void check(final String fullPath, final ControlFlow flow) {
    final String actualCFG = StringUtil.join(flow.getInstructions(), Object::toString, "\n");
    UsefulTestCase.assertSameLinesWithFile(fullPath, actualCFG, true);
  }
}
