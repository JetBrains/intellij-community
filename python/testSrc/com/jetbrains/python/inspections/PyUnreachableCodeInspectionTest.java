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

public class PyUnreachableCodeInspectionTest extends PyInspectionTestCase {
  // All previous unreachable tests, feel free to split them
  public void testUnreachable() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, () -> doTest());
  }

  // PY-51564
  public void testWithNotContext() {
    doTestByText("""
class NotContext:
    pass

def no_context():
    with NotContext():
        raise ValueError("Something went wrong")
    <warning descr="This code is unreachable">print("unreachable")</warning>
    """);
  }

  // PY-51564
  public void testWithContextlibUnittest() {
    doTestByText("""
import contextlib
from contextlib import suppress
from unittest import TestCase

def cl():
    with suppress(Exception):
        raise ValueError("Something went wrong")
    print("reachable")
    
class A(TestCase):
    def f(self):
        with self.assertRaises(Exception):
            raise ValueError("Something went wrong")
        print("reachable")
    """);
  }

  // PY-51564
  public void testWith() {
    doTestByText(
      """
class Suppress:
    def __enter__(self): ...
    def __exit__(self, exc_type, exc_value, traceback) -> bool: ...

class NoSuppress:
    def __enter__(self): ...
    def __exit__(self, exc_type, exc_value, traceback) -> bool | None: ...

def sup(b):
    with Suppress():
        a = 42
        raise ValueError("Something went wrong")
    print("reachable")
    
    with Suppress():
        assert b
        a = 42
        assert False
    print("reachable")

def nosup(b):
    with NoSuppress():
        a = 42
        raise ValueError("Something went wrong")
    <warning descr="This code is unreachable">print("unreachable")</warning>
    
    with NoSuppress():
        assert b
        a = 42
        assert False
    <warning descr="This code is unreachable">print("unreachable")</warning>
        """
    );
  }

  // PY-51564
  public void testMiltipleWith() {
    doTestByText("""
import contextlib

@contextlib.contextmanager
def raising_exception_in_enter(p):
    if p:
        raise Exception
    yield


def f():
    with contextlib.suppress(Exception):
        return
    <warning descr="This code is unreachable">print("Unreachable")</warning>

def f2(p):
    with contextlib.suppress(Exception):
        with raising_exception_in_enter(p):
            return
    print("Reachable")


def f3(p):
    with contextlib.suppress(Exception), raising_exception_in_enter(p):
        return
    print("Reachable")
                   """);
  }

  // PY-51564
  public void testAsyncWith() {
    doTestByText("""
class AsyncSuppress:
    async def __aenter__(self): ...
    async def __aexit__(self, exc_type, exc_value, traceback) -> bool: ...

class AsyncNoSuppress:
    async def __aenter__(self): ...
    async def __aexit__(self, exc_type, exc_value, traceback) -> bool | None: ...

async def sup(b):
    async with AsyncSuppress():
        a = 42
        raise ValueError("Something went wrong")
    print("reachable")
    
    async with AsyncSuppress():
        assert b
        a = 42
        assert False
    print("reachable")

async def nosup(b):
    async with AsyncNoSuppress():
        a = 42
        raise ValueError("Something went wrong")
    <warning descr="This code is unreachable">print("unreachable")</warning>
    
    async with AsyncNoSuppress():
        assert b
        a = 42
        assert False
    <warning descr="This code is unreachable">print("unreachable")</warning>
    """);
  }

  // PY-25974
  public void testExprOrSysExitAssignedToVar() {
    doTest();
  }

  public void testWhileTrue() {
    doTestByText(
      """
        while True:
            pass
        <warning descr="This code is unreachable">print()</warning>
        """
    );
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
            <warning descr="This code is unreachable">a = 1
            a1 = 1</warning>

        if False:
            <warning descr="This code is unreachable">b = 1
            b1 = 1</warning>
        else:
            pass

        if False:
            <warning descr="This code is unreachable">c = 1
            c1 = 1</warning>
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
            <warning descr="This code is unreachable">b = 1
            b1 = 1</warning>

        if True:
            pass
        elif c:
            <warning descr="This code is unreachable">d = 1
            d1 = 1</warning>
        else:
            <warning descr="This code is unreachable">e = 1
            e1 = 1</warning>
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
            <warning descr="This code is unreachable">e = 1
            f = 2</warning>
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
            <warning descr="This code is unreachable">a = 1
            a1 = 1</warning>

        if d:
            pass
        elif False:
            <warning descr="This code is unreachable">b = 1
            b1 = 1</warning>
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
  public void testUnreachableCodeReportedAfterSelfFail() {
    doTest();
  }

  // PY-24273
  public void testUnreachableCodeReportedAfterNoReturnFunction() {
    doTest();
  }

  public void testUnreachableCodeReportedAfterNoReturnWithQuotesFunction() {
    doTest();
  }

  // PY-24273
  public void testUnreachableCodeReportedAfterImportedNoReturnFunction() {
    doMultiFileTest();
  }

  public void testUnreachableCodeReportedNoReturnInClassMember() {
    doMultiFileTest();
  }

  // PY-53703
  public void testUnreachableCodeReportedAfterNever() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doTest();
    });
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
