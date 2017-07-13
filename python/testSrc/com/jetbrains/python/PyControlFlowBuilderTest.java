/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    setLanguageLevel(LanguageLevel.PYTHON30);
    try {
      doTest();
    }
    finally {
      setLanguageLevel(null);
    }
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
