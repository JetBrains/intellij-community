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

  // PY-81676
  public void testUnreachableBranchesAreStillConnected() {
    // This test ensures that the unreachable branch still
    // has some edges to and from the main CFG, which allows us
    // to make an exception for assert_never or assert False
    doTestByText("""
from typing import assert_never

if False:
    <warning descr="This code is unreachable">print("actually unreachable")
    print("actually unreachable")</warning>

if False:
    assert False
    <warning descr="This code is unreachable">print("actually unreachable")</warning>
    <warning descr="This code is unreachable">print("actually unreachable")</warning>

def f(obj: int) -> None:
    match scalar:
        case <error descr="Pattern makes remaining case clauses unreachable">x</error>:
            print("everything")
        case <error descr="Pattern makes remaining case clauses unreachable">_</error>:
            <warning descr="This code is unreachable">print("actually unreachable")
            print("actually unreachable")</warning>
        case _ as unreachable:
            assert_never(unreachable)
            <warning descr="This code is unreachable">print("actually unreachable")</warning>
            <warning descr="This code is unreachable">print("actually unreachable")</warning>
                   """);
  }

  // PY-82712
  public void testIfInsideTryExcept() {
    doTestByText("""
try:
    if a==1:
        print(0)
    else:
        print(1)
finally:
    print(2)
print("Reachable")
                   """);
  }

  // PY-81608
  public void testWhileInsideTryExcept() {
    doTestByText("""
try:
    x = True
    while x and not condition():
        print('a')
        x = False
finally:
    print('b')
print('Reachable')
                   """);
  }

  // PY-81482
  public void testTryAssertFinally() {
    doTestByText("""
try:
    assert True
finally:
    pass
print('Reachable')
                   """);
  }

  // PY-81936
  public void testUnreachableWithLangLevel() {
    runWithLanguageLevel(LanguageLevel.PYTHON310, () -> doTestByText("""
import sys

if sys.version_info < (2, 7):
    <warning descr="This code is unreachable">print("Unreachable")</warning>

if sys.version_info > (3, 11):
    <warning descr="This code is unreachable">print("Unreachable")</warning>
                   """));
  }
  
  // PY-81947
  public void testAnyOrNoneAfterIsNotNoneCast(){
    doTestByText("""
def func(x: Any | None = None):
    if x is not None:
        print("foo")
                   """);
  }
  
  // PY-82707
  public void testClassInheritingFromAny() {
    doTestByText("""
from typing import Any

class A(Any): ...

a = A()
if a is not None:
    print("hi")  # reachable

match a:
    case int():
        print()
    case str():
        print()  # reachable
                   """);
  }

  // PY-81729
  public void testTypeVarOrNoneAfterIsNotNoneCast(){
    doTestByText("""
def func[T](x: T | None = None) -> T | None:
    if x is not None:
        print("foo")
    return x
                   """);
  }

  // PY-81674
  public void testFinallyEarlyExit() {
    doTestByText("""
def f():
    try:
        print("Hello, world!")
    finally:
        assert False
        <warning descr="This code is unreachable">print("Goodbye, world!")</warning>
        
    <warning descr="This code is unreachable">print("This is unreachable")</warning>
                   """);
  }
  
  // PY-81676
  public void testTerminatingInBranch() {
    doTestByText("""
from enum import Enum
from typing import assert_never

class Foo(Enum):
    A = 0
    B = 1

def f1(foo: Foo) -> None:
    if foo is Foo.A:
        ...
    elif foo is Foo.B:
        ...
    else:
        assert_never(foo)
        <warning descr="This code is unreachable">print("unreachable");</warning>

def f2(foo: Foo) -> None:
    match foo:
        case Foo.A:
            ...
        case Foo.B:
            ...
        case _:
            assert_never(foo)
            <warning descr="This code is unreachable">print("unreachable");</warning>
                   """);
  }
  
  // PY-81674
  public void testConsecutiveTerminating() {
    doTestByText("""
def f1():
    exit()
    <warning descr="This code is unreachable">raise Exception()</warning>
    <warning descr="This code is unreachable">print("unreachable")</warning>
    <warning descr="This code is unreachable">assert False</warning>
    
def f2():
    raise Exception()
    <warning descr="This code is unreachable">assert False</warning>
    <warning descr="This code is unreachable">print("unreachable")</warning>
    <warning descr="This code is unreachable">exit()</warning>
    
def f3():
    assert False
    <warning descr="This code is unreachable">exit()</warning>
    <warning descr="This code is unreachable">print("unreachable")</warning>
    <warning descr="This code is unreachable">raise Exception()</warning>
                   """);
  }

  // PY-81674
  public void testNoNestedWarnings() {
    doTestByText("""
from enum import Enum

class Foo(Enum):
    A = 0
    B = 1

print(exit())

<warning descr="This code is unreachable">def unreachable(foo: Foo) -> None:
    if foo is Foo.A:
        ...
    elif foo is Foo.B:
        ...
    else:
        print("also unreachable")</warning>
                   """);
  }

  // PY-81593
  public void testReachabilityLogicalOperatorChaining() {
    doTestByText("""
def react(char1: str, char2: str) -> bool:
    return char1 != char2 and char1.lower() == char2.lower()
    #                         ^^^^^ Reachable
                   """);
  }

  // PY-81593
  public void testReachabilityInLoopWithImplicitTypeNarrowing() {
    doTestByText("""
class Dot:
    def __init__(self):
        self.closest: int | None = None

def largest_area(dots: list[Dot]) -> int:
    for dot in dots:
        if dot.closest is None:
            continue
        print("reachable")

    return 42
                   """);
  }
  
  // PY-79986
  public void testForInsideFinally() {
    doTestByText("""
def bar():
    try:
        return
    finally:
        for i in range(10):
            print("reachable")
                   """);
  }

  // TODO: Test pattern matching more when we have Never type
  // PY-79770
  public void testBasicPatternMatching() {
    doTestByText("""
def foo(param: int) -> int:
    match param:
        case _:
            return 41
                   """);
  }
  
  // PY-80471
  public void testIfTrueForLoop() {
    doTestByText("""
if True:
    for i in []:
        pass
else:
    <warning descr="This code is unreachable">print("unreachable")</warning>
                   """);
  }

  // PY-80471
  public void testIfTrueWhileLoop() {
    doTestByText("""
if True:
    while expr:
        break
else:
    <warning descr="This code is unreachable">print("unreachable")</warning>
                   """);
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
from typing import Literal

class Suppress:
    def __enter__(self): ...
    def __exit__(self, exc_type, exc_value, traceback) -> bool: ...

class Suppress2:
    def __enter__(self): ...
    def __exit__(self, exc_type, exc_value, traceback) -> Literal[True]: ...

class NoSuppress:
    def __enter__(self): ...
    def __exit__(self, exc_type, exc_value, traceback) -> bool | None: ...

class NoSuppress2:
    def __enter__(self): ...
    def __exit__(self, exc_type, exc_value, traceback) -> Literal[False]: ...

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

def sup2(b):
    with Suppress2():
        a = 42
        raise ValueError("Something went wrong")
    print("reachable")
    
    with Suppress2():
        assert b
        a = 42
        assert False
    print("reachable")

def nosupRaise(b):
    with NoSuppress():
        a = 42
        raise ValueError("Something went wrong")
    <warning descr="This code is unreachable">print("unreachable")</warning>
    
def nosupAssert(b):
    with NoSuppress():
        assert b
        a = 42
        assert False
    <warning descr="This code is unreachable">print("unreachable")</warning>

def nosup2Raise(b):
    with NoSuppress2():
        a = 42
        raise ValueError("Something went wrong")
    <warning descr="This code is unreachable">print("unreachable")</warning>
    
def nosup2Assert(b):
    with NoSuppress2():
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

async def nosupRaise(b):
    async with AsyncNoSuppress():
        a = 42
        raise ValueError("Something went wrong")
    <warning descr="This code is unreachable">print("unreachable")</warning>
    
async def nosupAssertFalse(b):
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
