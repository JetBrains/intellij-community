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
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyUnreachableCodeInspectionTest extends PyInspectionTestCase {
  // All previous unreachable tests, feel free to split them
  public void testUnreachable() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, () -> doTest());
  }

  // PY-7420, PY-16419, PY-26417
  public void testWithSuppressedExceptions() {
    doTest();
  }

  // PY-7420, PY-16419, PY-26417
  public void testWithNotSuppressedExceptions() {
    doTestByText(
      """
        class C(object):
            def __enter__(self):
                return self

            def __exit__(self, exc, value, traceback):
                return False

        def f1():
            with C():
                raise Exception()
            print(1) #pass

        def g2():
            raise Exception()

        def f2():
            with C():
                return g2()
            <warning descr="This code is unreachable">print(1) #pass</warning>

        def f3():
            with C():
                g2()
            print(1) #pass"""
    );
  }

  // PY-25974
  public void testExprOrSysExitAssignedToVar() {
    doTest();
  }

  // PY-22184
  public void testWhileTrueTryBreakFinally() {
    doTest();
  }

  // PY-24750
  public void testIfFalse() {
    doTestByText(
      """
        if False:
            <warning descr="This code is unreachable">a = 1</warning>

        if False:
            <warning descr="This code is unreachable">b = 1</warning>
        else:
            pass

        if False:
            <warning descr="This code is unreachable">c = 1</warning>
        elif d:
            pass
        else:
            pass
        """
    );
  }

  // PY-24750
  public void testIfTrue() {
    doTestByText(
      """
        if True:
            pass

        if True:
            pass
        else:
            <warning descr="This code is unreachable">b = 1</warning>

        if True:
            pass
        elif c:
            <warning descr="This code is unreachable">d = 1</warning>
        else:
            <warning descr="This code is unreachable">e = 1</warning>
        """
    );
  }

  // PY-24750
  public void testIfElifTrue() {
    doTestByText(
      """
        if c:
            pass
        elif True:
            pass

        if d:
            pass
        elif True:
            pass
        else:
            <warning descr="This code is unreachable">e = 1</warning>
        """
    );
  }

  // PY-24750
  public void testIfElifFalse() {
    doTestByText(
      """
        if c:
            pass
        elif False:
            <warning descr="This code is unreachable">a = 1</warning>

        if d:
            pass
        elif False:
            <warning descr="This code is unreachable">b = 1</warning>
        else:
            pass"""
    );
  }

  // PY-28972
  public void testWhileTrueElse() {
    doTestByText(
      """
        while True:
            pass
        else:
            <warning descr="This code is unreachable">print("ok")</warning>"""
    );
  }

  // PY-29435
  public void testNoInspectionIfDunderDebug() {
    doTestByText(
      """
        if __debug__:
            print('why not?')
        else:
            x = 42"""
    );
  }

  // PY-29767
  public void testContinueInPositiveIterationWithExitPoint() {
    doTestByText(
      """
        import sys

        for s in "abc":
            if len(s) == 1:
                continue
            sys.exit(0)
        raise Exception("the end")"""
    );
  }

  // PY-48760
  public void testContinueInCaseClause() {
    doTest();
  }

  // PY-48760
  public void testBreakInCaseClause() {
    doTest();
  }

  // PY-48760
  public void testReturnInCaseClause() {
    doTest();
  }

  // PY-48760
  public void testUnreachablePatternAfterIrrefutableCaseClause() {
    doTest();
  }

  // PY-48760
  public void testUnreachablePatternAfterIrrefutableOrPatternAlternative() {
    doTest();
  }

  // PY-7758
  public void testUnreachableCodeReportedAfterBuiltinExit() {
    doTest();
  }

  // PY-23859
  public void testUnreachableCodeReportedAfterSelfFailInClassContainingTestInName() {
    doTest();
  }

  // PY-23859
  public void testCodeNotReportedAsUnreachableAfterSelfFailInClassNotContainingTestInName() {
    doTest();
  }

  public void testUnreachableCodeReportedAfterPytestFail() {
    doTest();
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyUnreachableCodeInspection.class;
  }

  @Override
  protected boolean isLowerCaseTestFile() {
    return false;
  }
}
