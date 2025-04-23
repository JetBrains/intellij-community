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
    doTest();
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
    doTest();
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
      """
        class C(object):
            def __enter__(self):
                return self

            def __exit__(self, exc, value, traceback):
                return undefined

        def g1():
            raise Exception()

        def f1():
            with C():
                if undefined:
                    return g1()
                x = 2
            print(x) #pass

        def f2():
            with C():
                if undefined:
                    g1()
                x = 2
            print(x) #pass

        import contextlib
        from unittest import TestCase

        def f1():
            with contextlib.suppress(Exception):
                if undefined:
                    return g1()
                x = 2
            print(x) #pass

        class A(TestCase):
            def f2(self):
                with self.assertRaises(Exception):
                    if undefined:
                        g1()
                    x = 2
                print(x) #pass"""
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
    doTest();
  }

  // PY-23003
  public void testAccessInNestedLoop() {
    doTestByText(
      """
        for file in ['test_file']:
            for line in f:
                if a:
                    block = True
                elif <warning descr="Name 'block' can be undefined">block</warning> and b:
                    block = False
                else:
                    print(line)
            print(block)"""
    );
  }

  // PY-31834
  public void testTargetIsTypeHintNotDefinition() {
    doTestByText("a: int\n" +
                 "print(<warning descr=\"Name 'a' can be undefined\">a</warning>)");
  }

  // PY-31834
  public void testTargetWithoutAssignedValueButInitialized() {
    doTestByText("""
                   for var in range(10):
                       print(var)

                   with undefined as val:
                       print(val)""");
  }

  // PY-33886
  public void testAssignmentExpressions() {
    doTestByText(
      """
        def foo():
            if any((comment := line).startswith('#') for line in lines):
                print("First comment:", comment)
            else:
                print("There are no comments")

            if all((nonblank := line).strip() == '' for line in lines):
                print("All lines are blank")
            else:
                print("First non-blank line:", nonblank)


        def bar():
            [(comment := line).startswith('#') for line in lines]
            print(<warning descr="Local variable 'comment' might be referenced before assignment">comment</warning>)


        def baz():
            while (line := input()) and any((first_digit := c).isdigit() for c in line):
                print(line, first_digit)


        def more():
            if (x := True) or (y := 'spam'):
                print(<warning descr="Local variable 'y' might be referenced before assignment">y</warning>)
        """
    );
  }
  
  // PY-46592
  public void testUseParameterAfterDeletingAttribute() {
    doTestByText("""
        def func(foo, bar):
            del foo.bar
            print(bar)  # false positive
        """);
  }

  // PY-4537
  public void testReferencedAfterDeletion() {
    doTest();
  }

  // PY-4537
  public void testAfterDeletionByIndex() {
    doTest();
  }

  // PY-4537
  public void testAfterDeletionBySlice() {
    doTest();
  }

  // PY-4537
  public void testAfterDeletionGlobal() {
    doTest();
  }

  // PY-4537
  public void testAfterDeletionNonLocal() {
    doTest();
  }

  // PY-4537
  public void testConditionallyDeleted() {
    doTest();
  }

  // PY-39262
  public void testAssignmentExpressionInsideAndInIf() {
    doTestByText("if undefined1 and (r := undefined2()):\n" +
                 "    print(r)");
  }

  // PY-39262
  public void testAssignmentExpressionInsideBinaryInWhile() {
    doTestByText("while undefined1 and (r := undefined2()):\n" +
                 "    print(r)");

    doTestByText("while undefined1 or (r := undefined2()):\n" +
                 "    print(<warning descr=\"Name 'r' can be undefined\">r</warning>)");
  }

  // PY-48760
  public void testCapturePatternNameUsedAfterMatchStatement() {
    doTest();
  }

  // PY-48760
  public void testOrPatternAlternativesDefineDifferentNames() {
    doTest();
  }

  // PY-48760
  public void testNameDefinedInCaseClauseBodyUsedAfterMatchStatement() {
    doTest();
  }

  // PY-7758
  public void testVariableNotReportedAfterBuiltinExit() {
    doTest();
  }

  public void testVariableReportedAfterNotBuiltinExit() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doTestByText("""
                     def foo(x):
                       exit = lambda : 1
                       if x:
                          y = 1
                       else:
                         exit()
                       print(<warning descr="Local variable 'y' might be referenced before assignment">y</warning>)
                     """);
    });
  }

  public void testDoReportInFakeExit() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doTestByText("""
                     def foo(x):
                       exit = lambda : 1
                       if x:
                          y = 1
                       if not x:
                         exit()
                         print(<warning descr="Local variable 'y' might be referenced before assignment">y</warning>)
                     """);
    });
  }


  public void testDoNoReportInUnreachableCode() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doTestByText("""
                     def foo(x):
                       if x:
                          y = 1
                       if not x:
                         exit()
                         print(y)
                     """);
    });
  }

  // PY-63357
  public void testFunctionParameterAnnotatedWithReferenceToTypeParameter() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      doTestByText("""
                   def foo[T](x: T):
                       pass
                   """);
    });
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
