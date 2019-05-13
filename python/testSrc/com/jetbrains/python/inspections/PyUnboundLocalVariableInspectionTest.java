/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyUnboundLocalVariableInspectionTest extends PyInspectionTestCase {
  public void testSimple() {
    doTest();
  }

  // PY-1138
  public void testControlFlowInAssert() {
    doTest();
  }

  // PY-1176
  public void testLocalFunctionAndVariable() {
    doTest();
  }

  // PY-1359
  public void testUnboundLoopVariable() {
    doTest();
  }

  // PY-1408
  public void testUnboundExceptAs() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> doTest());
  }

  // PY-1434
  public void testClassLevelSameName() {
    doTest();
  }

  // PY-1435
  public void testInstanceFieldAndGlobal() {
    doTest();
  }

  // PY-3343
  public void testUnboundVariableFunctionCall() {
    doTest();
  }

  // PY-3407
  public void testUnboundNestedComprehension() {
    doTest();
  }

  // PY-3503
  public void testControlFlowInTryExceptFinally() {
    doTest();
  }

  // PY-3550
  public void testDefaultArgument() {
    doTest();
  }

  // PY-3583
  public void testUnboundConditionalImport() {
    doTest();
  }

  // PY-3603
  public void testUnboundNonLocal() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> doTest());
  }

  // PY-3671
  public void testUnboundConditionalImportAlias() {
    doTest();
  }

  // PY-3665
  public void testUnboundConditionalImportQualified() {
    doTest();
  }

  // PY-3651
  public void testUnboundAugmentedAssignment() {
    doTest();
  }

  // PY-3702
  public void testSysExit() {
    doTest();
  }

  // PY-3895
  public void testDecoratorAndParameter() {
    doTest();
  }

  // PY-4102
  public void testDefinedInTryUsedAfter() {
    doTest();
  }

  // PY-4150
  public void testParamAfterTryExcept() {
    doTest();
  }

  // PY-4151
  public void testUnboundDefinedInTryUsedAfterExcept() {
    doTest();
  }

  // PY-4152
  public void testDefinedInTryAndExcept() {
    doTest();
  }

  // PY-4157
  public void testDefinedInTryElse() {
    doTest();
  }

  // PY-4197
  public void testUnboundSwapStrUnicode() {
    doTest();
  }

  // PY-4229
  public void testInstanceAttributeOutsideClass() {
    doTest();
  }

  // PY-4297
  public void testOuterFunctionsAndSelfAttributes() {
    doTest();
  }

  // PY-4623
  public void testBuiltinAndSelfAttribute() {
    doTest();
  }

  // PY-4609
  public void testImplicitIfNotNone() {
    doTest();
  }

  // PY-4239
  public void testForBreakElse() {
    doTest();
  }

  // PY-5592
  public void testStarImportTopLevel() {
    doTest();
  }

  // PY-7966
  public void testUseAfterWithAndRaise() {
    doTest();
  }

  // PY-13919
  public void testRaiseInsideWith() {
    doTest();
  }

  // PY-16419, PY-26417
  public void testExitPointInsideWith() {
    doTestByText(
      "class C(object):\n" +
      "    def __enter__(self):\n" +
      "        return self\n" +
      "\n" +
      "    def __exit__(self, exc, value, traceback):\n" +
      "        return undefined\n" +
      "\n" +
      "def g1():\n" +
      "    raise Exception()\n" +
      "\n" +
      "def f1():\n" +
      "    with C():\n" +
      "        if undefined:\n" +
      "            return g1()\n" +
      "        x = 2\n" +
      "    print(x) #pass\n" +
      "\n" +
      "def f2():\n" +
      "    with C():\n" +
      "        if undefined:\n" +
      "            g1()\n" +
      "        x = 2\n" +
      "    print(x) #pass\n" +
      "\n" +
      "import contextlib\n" +
      "from unittest import TestCase\n" +
      "\n" +
      "def f1():\n" +
      "    with contextlib.suppress(Exception):\n" +
      "        if undefined:\n" +
      "            return g1()\n" +
      "        x = 2\n" +
      "    print(x) #pass\n" +
      "\n" +
      "class A(TestCase):\n" +
      "    def f2(self):\n" +
      "        with self.assertRaises(Exception):\n" +
      "            if undefined:\n" +
      "                g1()\n" +
      "            x = 2\n" +
      "        print(x) #pass"
    );
  }

  // PY-6114
  public void testUnboundUnreachable() {
    doTest();
  }

  // PY-1177
  public void testWhileTrueBreak() {
    doTest();
  }

  public void testWhileOneBreak() {
    doTest();
  }

  // PY-14840
  // PY-22003
  public void testPositiveIteration() {
    doTest();
  }

  public void testTooLargeToAnalyze() {
    doTest();
  }

  public void testForwardReferenceInAnnotations() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, () -> doTest());
  }

  // PY-23003
  public void testAccessInNestedLoop() {
    doTestByText(
      "for file in ['test_file']:\n" +
      "    for line in f:\n" +
      "        if a:\n" +
      "            block = True\n" +
      "        elif <warning descr=\"Name 'block' can be not defined\">block</warning> and b:\n" +
      "            block = False\n" +
      "        else:\n" +
      "            print(line)\n" +
      "    print(block)"
    );
  }

  // PY-31834
  public void testTargetIsTypeHintNotDefinition() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("a: int\n" +
                         "print(<warning descr=\"Name 'a' can be not defined\">a</warning>)")
    );
  }

  // PY-31834
  public void testTargetWithoutAssignedValueButInitialized() {
    doTestByText("for var in range(10):\n" +
                 "    print(var)\n" +
                 "\n" +
                 "with undefined as val:\n" +
                 "    print(val)");
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyUnboundLocalVariableInspection.class;
  }

  @Override
  protected boolean isLowerCaseTestFile() {
    return false;
  }
}
