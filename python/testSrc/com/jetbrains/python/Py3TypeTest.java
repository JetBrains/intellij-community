// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyTypeCheckerInspectionTest;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.psi.types.PyTypeChecker.GenericSubstitutions;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class Py3TypeTest extends PyTestCase {
  public static final String TEST_DIRECTORY = "/types/";

  // PY-21069
  public void testDunderGetattr() {
    doTest("MyClass", """
      class MyClass:
          def __getattr__(self, item) -> 'MyClass':
              pass
      
      expr = MyClass().attr
      """);
  }

  // PY-78964
  public void testFunctionReturnTypeTryFinally() {
    doTest("str",
           """
             def test():
                 try:
                     return 42
                 finally:
                     return "str"
             
                 return True
             
             expr = test()
             """);
  }

  // PY-20710
  public void testLambdaGenerator() {
    doTest("Generator[int, Any, Any]", """
              expr = (lambda: (yield 1))()
             """);
  }

  // PY-20710
  public void testGeneratorDelegatingToLambdaGenerator() {
    doTest("Generator[int, Any, str]", """
              def g():
                  yield from (lambda: (yield 1))()
                  return "foo"
              expr = g()
             """);
  }

  // PY-20710
  public void testYieldExpressionTypeFromGeneratorSendTypeHint() {
    doTest("int", """
      from typing import Generator
      
      def g() -> Generator[str, int, None]:
          expr = yield "foo"
      """);
  }

  // PY-20710
  public void testYieldFromExpressionTypeFromGeneratorReturnTypeHint() {
    doTest("int", """
      from typing import Generator, Any
      
      def delegate() -> Generator[None, Any, int]:
          yield
          return 42

      def g():
          expr = yield from delegate()
      """);
  }

  // PY-20710
  public void testYieldFromLambda() {
    doTest("Generator[int | str, str | Any, bool]",
           """
           from typing import Generator
           
           def gen1() -> Generator[int, str, bool]:
               yield 42
               return True
           
           def gen2():
               yield "str"
               return True
     
           l = lambda: (yield from gen1()) or (yield from gen2())
           expr = l()
           """);
  }

  //// PY-76659
  //public void testRecursiveResolve() {
  //  doTest("int",
  //         """
  //           x = 42
  //           while x:
  //               x = x + 1
  //           expr = x""");
  //}
  //
  //// PY-76659
  //public void testRecursiveResolve2() {
  //  doTest("int",
  //         """
  //           x = 42
  //           b: bool = ...
  //           while x:
  //               if b:
  //                   x = x + 1
  //                   expr = x
  //               else:
  //                   x = x - 1
  //           """);
  //}
  //
  //// PY-76659
  //public void testDeclareAfterUse() {
  //  doTest("int | Any",
  //         """
  //           from typing import Any, TypeGuard
  //
  //           def is_positive_integer(value: Any) -> TypeGuard[int]:
  //               return isinstance(value, int) and value > 0
  //
  //           def bar() -> object:
  //               return 321
  //
  //           def foo():
  //               for i in range(1, 100):
  //                   if i > 1:
  //                       expr = x
  //                   x = bar()
  //                   if not is_positive_integer(x):
  //                       break
  //           """);
  //}
  //
  //// PY-76659
  //public void testClassChain() {
  //  doTest("B | C | D | A",
  //         """
  //           class A:
  //               def bar() -> "B":
  //                   return B()
  //           class B:
  //               def bar() -> "C":
  //                   return C()
  //           class C:
  //               def bar() -> "D":
  //                   return D()
  //           class D:
  //               def bar() -> A:
  //                   return A()
  //
  //           def foo(b):
  //               x = A()
  //               while b:
  //                   x = x.bar()
  //
  //               expr = x""");
  //}

  // PY-6702
  public void testYieldFromType() {
    doTest("str | int | float",
           """
             def subgen():
                 for i in [1, 2, 3]:
                     yield i

             def gen():
                 yield 'foo'
                 yield from subgen()
                 yield 3.14

             for expr in gen():
                 pass
             """);
  }

  // PY-12944
  public void testYieldFromReturnType() {
    doTest("None",
           """
             def a():
                 yield 1
                 return 'a'

             y = [1, 2, 3]

             def b():
                 expr = yield from y
                 return expr
             """);
    doTest("str",
           """
             def a():
                 yield 1
                 return 'a'

             def b():
                 expr = yield from a()
                 return expr
             """);
    doTest("int",
           """
             def g():
                 yield 1
                 return 'abc'

             def f()
                 x = yield from g()

             for expr in f():
                 pass""");
  }

  public void testYieldFromHomogeneousTuple() {
    doTest("str",
           """
             import typing
             def get_tuple() -> typing.Tuple[str, ...]:
                 pass
             def gen()
                 yield from get_tuple()
             for expr in gen():    pass""");
  }

  public void testYieldFromHeterogeneousTuple() {
    doTest("int | str",
           """
             import typing
             def get_tuple() -> typing.Tuple[int, int, str]:
                 pass
             def gen()
                 yield from get_tuple()
             for expr in gen():    pass""");
  }

  public void testYieldFromUnknownTuple() {
    doTest("Any",
           """
             def get_tuple() -> tuple:
                 pass
             def gen()
                 yield from get_tuple()
             for expr in gen():    pass""");
  }

  public void testYieldFromUnknownList() {
    doTest("Any",
           """
             def get_list() -> list:
                 pass
             def gen()
                 yield from get_list()
             for expr in gen():    pass""");
  }

  public void testYieldFromUnknownDict() {
    doTest("Any",
           """
             def get_dict() -> dict:
                 pass
             def gen()
                 yield from get_dict()
             for expr in gen():    pass""");
  }

  public void testYieldFromUnknownSet() {
    doTest("Any",
           """
             def get_set() -> set:
                 pass
             def gen()
                 yield from get_set()
             for expr in gen():    pass""");
  }

  public void testAwaitAwaitable() {
    doTest("int",
           """
             class C:
                 def __await__(self):
                     yield 'foo'
                     return 0

             async def foo():
                 c = C()
                 expr = await c
             """);
  }

  public void testAsyncDefReturnType() {
    doTest("Coroutine[Any, Any, int]",
           """
             async def foo(x):
                 await x
                 return 0

             def bar(y):
                 expr = foo(y)
             """);
  }

  public void testAwaitCoroutine() {
    doTest("int",
           """
             async def foo(x):
                 await x
                 return 0

             async def bar(y):
                 expr = await foo(y)
             """);
  }

  // Not in PEP 484 as for now, see https://github.com/ambv/typehinting/issues/119
  public void testCoroutineReturnTypeAnnotation() {
    doTest("int",
           """
             async def foo() -> int: ...

             async def bar():
                 expr = await foo()
             """);
  }

  // PY-16987
  public void testNoTypeInGoogleDocstringParamAnnotation() {
    doTest("int", """
      def f(x: int):
          ""\"
          Args:
              x: foo
          ""\"   \s
          expr = x""");
  }

  // PY-16987
  public void testUnfilledTypeInGoogleDocstringParamAnnotation() {
    doTest("int", """
      def f(x: int):
          ""\"
          Args:
              x (): foo
          ""\"   \s
          expr = x""");
  }

  // PY-16987
  public void testNoTypeInNumpyDocstringParamAnnotation() {
    doTest("int", """
      def f(x: int):
          ""\"
          Parameters
          ----------
          x
              foo
          ""\"
          expr = x""");
  }

  // PY-17010
  public void testAnnotatedReturnTypePrecedesDocstring() {
    doTest("int", """
      def func() -> int:
          ""\"
          Returns:
              str
          ""\"
      expr = func()""");
  }

  // PY-17010
  public void testAnnotatedParamTypePrecedesDocstring() {
    doTest("int", """
      def func(x: int):
          ""\"
          Args:
              x (str):
          ""\"
          expr = x""");
  }

  public void testOpenDefault() {
    doTest("TextIOWrapper[_WrappedBuffer]",
           "expr = open('foo')\n");
  }

  public void testOpenText() {
    doTest("TextIOWrapper[_WrappedBuffer]",
           "expr = open('foo', 'r')\n");
  }

  public void testOpenBinary() {
    doTest("BufferedReader",
           "expr = open('foo', 'rb')\n");
  }

  public void testIoOpenDefault() {
    doTest("TextIOWrapper[_WrappedBuffer]",
           """
             import io
             expr = io.open('foo')
             """);
  }

  public void testIoOpenText() {
    doTest("TextIOWrapper[_WrappedBuffer]",
           """
             import io
             expr = io.open('foo', 'r')
             """);
  }

  public void testIoOpenBinary() {
    doTest("BufferedReader",
           """
             import io
             expr = io.open('foo', 'rb')
             """);
  }

  // PY-1427
  public void testBytesLiteral() {
    doTest("bytes", "expr = b'foo'");
  }

  // PY-20770
  public void testAsyncGenerator() {
    doTest("AsyncGenerator[int, Any]",
           """
             async def asyncgen():
                 yield 42
             expr = asyncgen()""");
  }

  // PY-20770
  public void testAsyncGeneratorDunderAiter() {
    doTest("AsyncIterator[int]",
           """
             async def asyncgen():
                 yield 42
             expr = asyncgen().__aiter__()""");
  }

  // PY-20770
  public void testAsyncGeneratorDunderAnext() {
    doTest("Awaitable[int]",
           """
             async def asyncgen():
                 yield 42
             expr = asyncgen().__anext__()""");
  }

  // PY-20770
  public void testAsyncGeneratorAwaitOnDunderAnext() {
    doTest("int",
           """
             async def asyncgen():
                 yield 42
             async def asyncusage()
                 expr = await asyncgen().__anext__()""");
  }

  // PY-20770
  public void testAsyncGeneratorAsend() {
    doTest("Coroutine[Any, Any, int]",
           """
             async def asyncgen():
                 yield 42
             expr = asyncgen().asend("hello")""");
  }

  // PY-20770
  public void testAsyncGeneratorAwaitOnAsend() {
    doTest("int",
           """
             async def asyncgen():
                 yield 42
             async def asyncusage():
                 expr = await asyncgen().asend("hello")""");
  }

  // PY-20770
  public void testIteratedAsyncGeneratorElement() {
    doTest("int",
           """
             async def asyncgen():
                 yield 10
             async def run():
                 async for i in asyncgen():
                     expr = i""");
  }

  // PY-20770
  public void testElementInAsyncComprehensions() {
    doTest("int",
           """
             async def asyncgen():
                 yield 10
             async def run():
                 {expr async for expr in asyncgen()}
             """);

    doTest("int",
           """
             async def asyncgen():
                 yield 10
             async def run():
                 [expr async for expr in asyncgen()]
             """);

    doTest("int",
           """
             async def asyncgen():
                 yield 10
             async def run():
                 {expr: expr ** 2 async for expr in asyncgen()}
             """);

    doTest("int",
           """
             async def asyncgen():
                 yield 10
             async def run():
                 (expr async for expr in asyncgen())
             """);

    doTest("int",
           """
             async def asyncgen():
                 yield 10
             async def run():
                 list(expr async for expr in asyncgen())
             """);

    doTest("int",
           """
             async def asyncgen():
                 yield 10
             async def run():
                 dataset = {data async for expr in asyncgen()
                                 async for data in asyncgen()
                                 if check(data)}
             """);

    doTest("int",
           """
             async def asyncgen():
                 yield 10
             async def run():
                 dataset = {expr async for line in asyncgen()
                                 async for expr in asyncgen()
                                 if check(expr)}
             """);
  }

  // PY-20770
  public void testAwaitInComprehensions() {
    doTest("list[int]",
           """
             async def asyncgen():
                 yield 10
             async def run():
                 expr = [await z for z in [asyncgen().__anext__()]]
             """);
  }

  // PY-20770
  public void testAwaitInAsyncComprehensions() {
    doTest("list[int]",
           """
             async def asyncgen():
                 yield 10
             async def asyncgen2():
                 yield asyncgen().__anext__()
             async def run():
                 expr = [await z async for z in asyncgen2()]
             """);
  }

  public void testIsNotNone() {
    doTest("int",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if x is not None:
                     expr = x
             """);

    doTest("int",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if None is not x:
                     expr = x
             """);

    doTest("int",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if not x is None:
                     expr = x
             """);

    doTest("int",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if not None is x:
                     expr = x
             """);
  }

  public void testIsNone() {
    doTest("None",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if x is None:
                     expr = x
             """);

    doTest("None",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if None is x:
                     expr = x
             """);
  }

  public void testIsEnumMember() {
    doTest("Literal[Answer.No, Answer.Yes]",
           """
             from enum import Enum
             
             class Answer(Enum):
                 Yes = 1
                 No = 2
             
             def foo(v: object):
                 if v is Answer.Yes or v is Answer.No:
                     expr = v
             """);
    doTest("Literal[Answer.No, Answer.Yes]",
           """
             from enum import Enum
             
             class Answer(Enum):
                 Yes = 1
                 No = 2

             def foo(v: object):
                 if v is not Answer.Yes and v is not Answer.No:
                     raise ValueError("Invalid value")
                 expr = v
             """);
    doTest("Literal[Answer.No, Answer.Yes]",
           """
             from enum import Enum

             class Answer(Enum):
                 Yes = 1
                 No = 2
             
             def foo(v: object):
                 assert v is Answer.Yes or v is Answer.No
                 expr = v
             """);
  }

  public void testLiteralTypeNarrowingEquals() {
    doTest("Literal[\"abba\"]",
           """
             from typing import Literal
             def foo(v: str):
                 if (v == "abba"):
                     expr = v
             """);
    doTest("Literal[\"ab\"]",
           """
             from typing import Literal
             def foo(v: Literal["abba", "ab"]):
                 if (v != "abba"):
                     expr = v
             """);
    doTest("Literal[\"abc\"]",
           """
             from typing import Literal
             abc: Literal["abc"] = "abc"
             def foo(v: str):
                 if (v == abc):
                     expr = v
             """);
    doTest("Literal[\"abba\"]",
           """
             from typing import Literal
             if ((v := input()) == "abba"):
                 expr = v
             """);
  }

  public void testLiteralTypeNarrowingIn() {
    doTest("Literal[1, 2]",
           """
             def f(a: int):
                 if a in (1, 2, ""):
                     expr = a
             """);
    doTest("Literal[-10, \"a\"]",
           """
             from enum import Enum
             class E(Enum):
                 A = 1
             def f(a: int | str):
                 if a in (-10, E.A, "a"):
                     expr = a
             """);
    doTest("Literal[\"abb\"]",
           """
             from typing import Literal
             def f(a: Literal[3, "abb", "ab", False]):
                 if a in ("abb", True):
                     expr = a
             """);
    doTest("Literal[3, \"ab\"]",
           """
             from typing import Literal
             def f(a: Literal[3, "abb", "ab", False]):
                 if a not in ("abb", False):
                     expr = a
             """);
    doTest("Literal[\"abb\", False]",
           """
             from typing import Literal
             def f(a: Literal[10, "abb", "ab", False]):
                 if a not in ("abb", False):
                     pass
                 else:
                     expr = a
             """);
    doTest("Literal[-1] | None",
           """
             def f(v: object):
                 if v in (-1, None):
                     expr = v
             """);
    doTest("Literal[\"abba\"]",
           """
             from typing import Literal
             if (a := input()) in ("abba", False):
                 expr = a
             """);
  }

  // PY-21083
  public void testFloatFromhex() {
    doTest("float",
           "expr = float.fromhex(\"0.5\")");
  }

  // PY-20073
  public void testMapReturnType() {
    doTest("int",
           "for x in map(lambda x: 42, 'foo'):\n" +
           "    expr = x");
  }

  // PY-20757
  public void testMinElseNone() {
    doTest("Any | None",
           """
             def get_value(v):
                 if v:
                     return min(v)
                 else:
                     return None
             expr = get_value([])""");
  }

  // PY-21350
  public void testBuiltinInput() {
    doTest("str",
           "expr = input()");
  }

  public void testMinResult() {
    doTest("int",
           "expr = min(1, 2, 3)");
  }

  public void testMaxResult() {
    doTest("int",
           "expr = max(1, 2, 3)");
  }

  // PY-21692
  public void testSumResult() {
    doTest("int",
           "expr = sum([1, 2, 3])");
  }

  public void testNumpyResolveRaterDoesNotIncreaseRateForNotNdarrayRightOperatorFoundInStub() {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    doTest("D1 | D2",
           """
             class D1(object):
                 pass
             class D2(object):
                 pass
             expr = D1() / D2()""");
  }

  // PY-22181
  public void testIterationOverIterableWithSeparateIterator() {
    doTest("int",
           """
             class AIter(object):
                 def __next__(self):
                     return 5
             class A(object):
                 def __iter__(self):
                     return AIter()
             a = A()
             for expr in a:
                 print(expr)""");
  }

  // PY-22181
  public void testAsyncIterationOverIterableWithSeparateIterator() {
    doTest("int",
           """
             class AIter(object):
                 async def __anext__(self):
                     return 5
             class A(object):
                 def __aiter__(self):
                     return AIter()
             a = A()
             async for expr in a:
                 print(expr)""");
  }

  // PY-60714
  public void testAsyncIteratorUnwrapsCoroutineFromAnext() {
    doTest("bytes", """
             class AIterator:
                 def __aiter__(self):
                     return self

                 async def __anext__(self) -> bytes:
                     return b"a"
             
             async for expr in AIterator():
                 print(expt)
             """);
  }

  // PY-21655
  public void testUsageOfFunctionDecoratedWithAsyncioCoroutine() {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    doTest("int",
           """
             import asyncio
             @asyncio.coroutine
             def foo():
                 yield from asyncio.sleep(1)
                 return 3
             async def bar():
                 expr = await foo()
                 return expr""");
  }

  // PY-21655
  public void testUsageOfFunctionDecoratedWithTypesCoroutine() {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    doTest("int",
           """
             import asyncio
             import types
             @types.coroutine
             def foo():
                 yield from asyncio.sleep(1)
                 return 3
             async def bar():
                 expr = await foo()
                 return expr""");
  }

  // PY-22513
  public void testGenericKwargs() {
    doTest("dict[str, int | str]",
           """
             from typing import Any, Dict, TypeVar

             T = TypeVar('T')

             def generic_kwargs(**kwargs: T) -> Dict[str, T]:
                 pass

             expr = generic_kwargs(a=1, b='foo')
             """);
  }

  // PY-19323
  public void testReturnedTypingCallable() {
    doTest("(...) -> Any",
           """
             from typing import Callable
             def f() -> Callable:
                 pass
             expr = f()""");
  }

  public void testReturnedTypingCallableWithUnknownParameters() {
    doTest("(...) -> int",
           """
             from typing import Callable
             def f() -> Callable[..., int]:
                 pass
             expr = f()""");
  }

  public void testReturnedTypingCallableWithKnownParameters() {
    doTest("(int, str) -> int",
           """
             from typing import Callable
             def f() -> Callable[[int, str], int]:
                 pass
             expr = f()""");
  }

  // PY-81606
  public void testCallable() {
    doTest("(x: int, /, s: str, *, k: bytes) -> None",
           """
             def func(x: int, /, s: str, *, k: bytes) -> None:
                 pass
             expr = func""");
  }

  // PY-24445
  public void testIsSubclassInsideListComprehension() {
    doTest("list[type[A]]",
           "class A: pass\n" +
           "expr = [e for e in [] if issubclass(e, A)]");
  }

  public void testIsInstanceInsideListComprehension() {
    doTest("list[A]",
           "class A: pass\n" +
           "expr = [e for e in [] if isinstance(e, A)]");
  }

  // PY-24405
  public void testAsyncWithType() {
    doTest("str",
           """
             class AContext:
                 async def __aenter__(self) -> str:
                     pass
             async def foo():
                 async with AContext() as c:
                     expr = c""");
  }

  // PY-24067
  public void testAsyncFunctionReturnTypeInDocstring() {
    doTest("Coroutine[Any, Any, int]",
           """
             async def f():
                 ""\"
                 :rtype: int
                 ""\"
                 pass
             expr = f()""");
  }

  // PY-27518
  public void testAsyncFunctionReturnTypeInNumpyDocstring() {
    doTest("Coroutine[Any, Any, int]",
           """
             async def f():
                 ""\"
                 An integer.

                 Returns
                 -------
                 int
                     A number
                 ""\"
                 pass
             expr = f()""");
  }

  // PY-26847
  public void testAwaitOnImportedCoroutine() {
    doMultiFileTest("Any",
                    """
                      from mycoroutines import mycoroutine

                      async def main():
                          expr = await mycoroutine()""");
  }

  // PY-26643
  public void testReplaceSelfInCoroutine() {
    doTest("Coroutine[Any, Any, B]",
           """
             class A:
                 async def foo(self):
                     return self
             class B(A):
                 pass
             expr = B().foo()""");
  }

  // PY-4813
  public void testParameterTypeInferenceInSubclassFromDocstring() {
    doTest("int",
           """
             class Base:
                 def test(self, param):
                     ""\"
                     :param param:
                     :type param: int
                     ""\"
                     pass

             class Subclass(Base):
                 def test(self, param):
                     expr = param""");
  }

  public void testParameterTypeInferenceInSubclassFromAnnotation() {
    doTest("int",
           """
             class Base:
                 def test(self, param: int) -> int: pass

             class Subclass(Base):
                 def test(self, param):
                     expr = param""");
  }

  public void testParameterTypeInferenceInSubclassHierarchyFromAnnotation1() {
    doTest("int",
           """
             class Base:
                 def test(self, param: int) -> int: pass

             class Base1(Base):
                 pass

             class Subclass(Base1):
                 def test(self, param):
                     expr = param""");
  }

  public void testParameterTypeInferenceInSubclassHierarchyFromAnnotation2() {
    doTest("int",
           """
             class Base1:
                 def test(self, param: int) -> int: pass

             class Base2:
                 def test(self, param: str) -> str: pass

             class Subclass(Base1, Base2):
                 def test(self, param):
                     expr = param""");
  }

  public void testParameterTypeInferenceInSubclassHierarchyFromAnnotation3() {
    doTest("int",
           """
             class Base1:
                 def test(self, param: int) -> int: pass

             class Base2:
                 def test(self, param: str) -> str: pass

             class Base3(Base1):
                 pass

             class Subclass(Base3, Base2):
                 def test(self, param):
                     expr = param""");
  }

  public void testParameterTypeInferenceInSubclassHierarchyFromAnnotation4() {
    /*
      This behavior mimics C3 MRO used by *new style* classes for Python 2.3 and below
      For details see: https://www.python.org/download/releases/2.3/mro/
      Since annotations are supported in Python 3.5 and above, *classic classes*
      should not be tested here, but it's NOT the case for docstrings
    */
    doTest("int",
           """
             class Base1:
                 def test(self, param: str) -> str: pass

             class Base2(Base1):
                 def test(self, param: int) -> int: pass

             class Base3(Base1):
                 pass

             class Subclass(Base3, Base2):
                 def test(self, param):
                     expr = param""");
  }

  public void testParameterTypeInferenceInSubclassHierarchyFromAnnotation5() {
    doTest("int",
           """
             class Base1:
                 def test(self, param: int) -> int: pass

             class Base2(Base1):
                 def test(self, param): pass

             class Subclass(Base2):
                 def test(self, param):
                     expr = param""");
  }

  public void testParameterTypeInferenceInOverloadedMethods() {
    doTest("Any",
           """
             from typing import overload

             class Base:
                 @overload
                 def test(self, param: int) -> int: pass

                 @overload
                 def test(self, param: str) -> str: pass

             class Subclass(Base):
                 def test(self, param):
                     expr = param""");
  }

  public void testParameterTypeInferenceInSubclassHierarchyInStaticMethods() {
    doTest("int",
           """
             class Base:
                 @staticmethod
                 def test(param: int, param1: int) -> int: pass

             class Subclass(Base):
                 @staticmethod
                 def test(param, param1):
                     expr = param

             """);
  }

  public void testReturnTypeInferenceInSubclassFromAnnotation() {
    doTest("int",
           """
             class Base:
                 def test(self) -> int: pass

             class Subclass(Base):
                 def test(self): pass

             expr = Subclass().test()""");
  }

  public void testReturnTypeInferenceInSubclassHierarchyFromAnnotation() {
    doTest("int",
           """
             class Base:
                 def test(self) -> int: pass

             class Base1(Base):
                 pass

             class Subclass(Base1):
                 def test(self): pass

             expr = Subclass().test()""");
  }

  /**
   * TODO: activate when return type information from :rtype: will be available in subclasses.
   *
   * See {@code {@link com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider#getReturnTypeFromSupertype}} javadoc.
   */
  public void ignoreTestReturnTypeInferenceInSubclassFromDocstring() {
    doTest("int",
           """
             class Base:
                 def test(self):        ""\"
                     :rtype: int
                     ""\"
                     pass

             class Subclass(Base):
                 def test(self): pass

             expr = Subclass().test()""");
  }

  /**
   * TODO: activate when return type information from :rtype: will be available in subclasses.
   *
   * See {@code {@link com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider#getReturnTypeFromSupertype}} javadoc.
   */
  public void ignoreTestReturnTypeInferenceInSubclassHierarchyFromDocstring() {
    doTest("int",
           """
             class Base:
                 def test(self):        ""\"
                     :rtype: int
                     ""\"
                     pass

             class Base1(Base):
                 pass

             class Subclass(Base1):
                 def test(self): pass

             expr = Subclass().test()""");
  }

  // PY-27398
  public void testDataclassPostInitParameter() {
    doTest("int",
           """
             from dataclasses import dataclass, InitVar
             @dataclass
             class Foo:
                 i: int
                 j: int
                 d: InitVar[int]
                 def __post_init__(self, d):
                     expr = d""");
  }

  // PY-78006
  public void testDataclassPostInitParameters() {
    doTest("tuple[A1, A4, A2, A3, A5, A6]",
           """
             from dataclasses import dataclass, InitVar
             
             class A1:
                 pass
             class A2:
                 pass
             class A3:
                 pass
             class A4:
                 pass
             class A5:
                 pass
             class A6:
                 pass
             
             @dataclass
             class BaseDC:
                 b1: str
                 a1: InitVar[A1]
             
             @dataclass
             class DC1(BaseDC):
                 a2: InitVar[A2]
                 b2: int
                 a3: InitVar[A3]
             
             @dataclass
             class DC2(BaseDC):
                 a4: InitVar[A4]
                 b3 = 2
             
             @dataclass
             class DC(DC1, DC2):
                 b4: bool
                 a5: InitVar[A5]
                 a6: InitVar[A6]
             
                 def __post_init__(self, p1, p2, p3, p4, p5, p6):
                     expr = (p1, p2, p3, p4, p5, p6)
             """);
  }

  // PY-27398
  public void testDataclassPostInitParameterNoInit() {
    doTest("Any",
           """
             from dataclasses import dataclass, InitVar
             @dataclass(init=False)
             class Foo:
                 i: int
                 j: int
                 d: InitVar[int]
                 def __post_init__(self, d):
                     expr = d""");
  }

  // PY-28506
  public void testDataclassPostInitInheritedParameter() {
    // both are dataclasses with enabled `init`
    doTest("int",
           """
             from dataclasses import dataclass, InitVar

             @dataclass
             class A:
                 a: InitVar[int]

             @dataclass
             class B(A):
                 b: InitVar[str]

                 def __post_init__(self, a, b):
                     expr = a""");
  }

  // PY-28506
  public void testDataclassPostInitInheritedParameter2() {
    // both are dataclasses, base with enabled `init`
    doTest("Any",
           """
             from dataclasses import dataclass, InitVar

             @dataclass
             class A:
                 a: InitVar[int]

             @dataclass(init=False)
             class B(A):
                 b: InitVar[str]

                 def __post_init__(self, a, b):
                     expr = a""");
  }

  // PY-28506
  public void testDataclassPostInitInheritedParameter3() {
    // both are dataclasses, derived with enabled `init`
    doTest("int",
           """
             from dataclasses import dataclass, InitVar

             @dataclass(init=False)
             class A:
                 a: InitVar[int]

             @dataclass
             class B(A):
                 b: InitVar[str]

                 def __post_init__(self, a, b):
                     expr = a""");
  }

  // PY-28506
  public void testDataclassPostInitInheritedParameter4() {
    // both are dataclasses with disabled `init`
    doTest("Any",
           """
             from dataclasses import dataclass, InitVar

             @dataclass(init=False)
             class A:
                 a: InitVar[int]

             @dataclass(init=False)
             class B(A):
                 b: InitVar[str]

                 def __post_init__(self, a, b):
                     expr = a""");
  }

  // PY-28506
  public void testMixedDataclassPostInitInheritedParameter() {
    doTest("str",
           """
             from dataclasses import dataclass, InitVar

             class A:
                 a: InitVar[int]

             @dataclass
             class B(A):
                 b: InitVar[str]

                 def __post_init__(self, a, b):
                     expr = a""");

    doTest("Any",
           """
             from dataclasses import dataclass, InitVar

             @dataclass
             class A:
                 a: InitVar[int]

             class B(A):
                 b: InitVar[str]

                 def __post_init__(self, a, b):
                     expr = a""");
  }

  // PY-27783
  public void testApplyingSuperSubstitutionToGenericClass() {
    doTest("dict[T, int]",
           """
             from typing import TypeVar, Generic, Dict, List

             T = TypeVar('T')

             class A(Generic[T]):
                 pass

             class B(A[List[T]], Generic[T]):
                 def __init__(self) -> None:
                     self.value_set: Dict[T, int] = {}

                 def foo(self) -> None:
                     expr = self.value_set""");
  }

  // PY-27783
  public void testApplyingSuperSubstitutionToBoundedGenericClass() {
    doTest("dict[T, int]",
           """
             from typing import TypeVar, Generic, Dict, List

             T = TypeVar('T', bound=str)

             class A(Generic[T]):
                 pass

             class B(A[List[T]], Generic[T]):
                 def __init__(self) -> None:
                     self.value_set: Dict[T, int] = {}

                 def foo(self) -> None:
                     expr = self.value_set""");
  }

  // PY-13750
  public void testBuiltinRound() {
    doTest("int", "expr = round(1)");
    doTest("int", "expr = round(1, 1)");

    doTest("int", "expr = round(1.1)");
    doTest("float", "expr = round(1.1, 1)");

    doTest("int", "expr = round(True)");
    doTest("int", "expr = round(True, 1)");
  }

  // PY-29665
  public void testRawBytesLiteral() {
    doTest("bytes", "expr = rb'raw bytes'");
    doTest("bytes", "expr = br'raw bytes'");
  }

  public void testFStringLiteralType() {
    doTest("str",
           "expr = f'foo'");
  }

  // PY-35885
  public void testFunctionDunderDoc() {
    doTest("str",
           """
             def example():
                 ""\"Example Docstring""\"
                 return 0
             expr = example.__doc__""");
  }

  // PY-29891
  public void testContextManagerType() {
    doTest("str",
           """
             from typing import Type, ContextManager
             def example():
               manager: Type[ContextManager[str]]
               with manager() as m:
                     expr = m""");
  }

  // PY-29891
  public void testAsyncContextManager() {
    doTest("str",
           """
             from typing import AsyncContextManager
             async def example():
                 manager: AsyncContextManager[str]
                 async with manager as m:
                     expr = m""");
  }

  // PY-49935
  public void testParamSpecExample() {
    doTest("(a: str, b: bool) -> str",
           """
             from typing import Callable, ParamSpec

             P = ParamSpec("P")


             def changes_return_type_to_str(x: Callable[P, int]) -> Callable[P, str]: ...


             def returns_int(a: str, b: bool) -> int:
                 return 42


             expr = changes_return_type_to_str(returns_int)""");
  }

  // PY-59127
  public void testParamSpecInImportedFile() {
    doMultiFileTest("(a: str, b: bool) -> str",
                    """
                      from mod import changes_return_type_to_str
                            
                      def returns_int(a: str, b: bool) -> int:
                          return 42

                      expr = changes_return_type_to_str(returns_int)
                      """);
  }

  public void testParamSpecArgsKwargsInAnnotations() {
    doTest("(c: (**P) -> int, **P, **P) -> None", """
      from typing import Callable, ParamSpec
      
      P = ParamSpec('P')
      
      def func(c: Callable[P, int], *args: P.args, **kwargs: P.kwargs) -> None:
          ...
      
      expr = func
      """);
  }

  public void testParamSpecArgsKwargsInTypeComments() {
    doTest("(c: (**P) -> int, **P, **P) -> None", """
      from typing import Callable, ParamSpec
      
      P = ParamSpec('P')
      
      def func(c, # type: Callable[P, int]
               *args, # type: P.args
               **kwargs, # type: P.kwargs
               ):
          # type: (...) -> None
          ...
      
      expr = func
      """);
  }

  public void testParamSpecArgsKwargsInFunctionTypeComment() {
    doTest("(c: (**P) -> int, **P, **P) -> None", """
      from typing import Callable, ParamSpec
      
      P = ParamSpec('P')
      
      def func(c, *args, **kwargs):
          # type: (Callable[P, int], *P.args, **P.kwargs) -> None
          ...
      
      expr = func
      """);
  }

  public void testParamSpecArgsKwargsInImportedFile() {
    doMultiFileTest("(c: (**P) -> int, **P, **P) -> None", """
      from mod import func
            
      expr = func
      """);
  }

  // PY-49935
  public void testParamSpecSeveral() {
    doTest("(y: int, x: str) -> bool",
           """
             from typing import ParamSpec, Callable

             P = ParamSpec("P")


             def foo(x: Callable[P, int], y: Callable[P, int]) -> Callable[P, bool]: ...


             def x_y(x: int, y: str) -> int: ...


             def y_x(y: int, x: str) -> int: ...


             expr = foo(x_y, y_x)""");
  }

  // PY-49935
  public void testParamSpecUserGenericClass() {
    doTest("Y[int, [q: int, p: str, r: bool]]",
           """
             from typing import TypeVar, Generic, Callable, ParamSpec

             U = TypeVar("U")
             P = ParamSpec("P")


             class Y(Generic[U, P]):
                 f: Callable[P, str]
                 attr: U

                 def __init__(self, f: Callable[P, str], attr: U) -> None:
                     self.f = f
                     self.attr = attr


             def a(q: int, p: str, r: bool) -> str: ...


             expr = Y(a, 1)
             """);
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethod() {
    doTest("(q: int) -> str",
           """
             from typing import TypeVar, Generic, Callable, ParamSpec

             U = TypeVar("U")
             P = ParamSpec("P")


             class Y(Generic[U, P]):
                 f: Callable[P, U]
                 attr: U

                 def __init__(self, f: Callable[P, U], attr: U) -> None:
                     self.f = f
                     self.attr = attr


             def a(q: int) -> str: ...


             expr = Y(a, '1').f
             """);
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethodConcatenate() {
    doTest("(int, s: str, b: bool) -> str",
           """
             from typing import TypeVar, Generic, Callable, ParamSpec, Concatenate

             U = TypeVar("U")
             P = ParamSpec("P")


             class Y(Generic[U, P]):
                 f: Callable[Concatenate[int, P], U]
                 attr: U

                 def __init__(self, f: Callable[Concatenate[int, P], U], attr: U) -> None:
                     self.f = f
                     self.attr = attr


             def a(q: int, s: str, b: bool) -> str: ...


             expr = Y(a, '1').f
             """);
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethodConcatenateSeveralParameters() {
    doTest("(int, bool, s: str, b: bool) -> str",
           """
             from typing import TypeVar, Generic, Callable, ParamSpec, Concatenate

             U = TypeVar("U")
             P = ParamSpec("P")


             class Y(Generic[U, P]):
                 f: Callable[Concatenate[int, bool, P], U]
                 attr: U

                 def __init__(self, f: Callable[Concatenate[int, bool, P], U], attr: U) -> None:
                     self.f = f
                     self.attr = attr


             def a(q: int, r: bool, s: str, b: bool) -> str: ...


             expr = Y(a, '1').f
             """);
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethodConcatenateOtherFunction() {
    doTest("(bool, dict[str, list[str]], s: str, b: bool) -> str",
           """
             from typing import TypeVar, Generic, Callable, ParamSpec, Concatenate

             U = TypeVar("U")
             P = ParamSpec("P")


             class Y(Generic[U, P]):
                 f: Callable[Concatenate[int, bool, P], U]
                 g: Callable[Concatenate[bool, dict[str, list[str]], P], U]
                 attr: U

                 def __init__(self, f: Callable[Concatenate[int, bool, P], U], attr: U) -> None:
                     self.f = f
                     self.attr = attr


             def a(q: int, r: bool, s: str, b: bool) -> str: ...


             expr = Y(a, '1').g
             """);
  }

  // PY-49935
  public void testParamSpecUserGenericClassAttribute() {
    doTest("str",
           """
             from typing import TypeVar, Generic, Callable, ParamSpec

             U = TypeVar("U")
             P = ParamSpec("P")


             class Y(Generic[U, P]):
                 f: Callable[P, U]
                 attr: U

                 def __init__(self, f: Callable[P, U], attr: U) -> None:
                     self.f = f
                     self.attr = attr


             def a(q: int) -> str: ...


             expr = Y(a, '1').attr
             """);
  }

  // PY-49935
  public void testParamSpecConcatenateAdd() {
    doTest("(str, x: int, args: tuple[bool, ...]) -> bool",
           """
             from typing import Callable, Concatenate, ParamSpec

             P = ParamSpec("P")


             def bar(x: int, *args: bool) -> int: ...


             def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


             expr = add(bar)  # Should return (__a: str, x: int, *args: bool) -> bool""");
  }

  // PY-49935
  public void testParamSpecConcatenateAddSeveralParameters() {
    doTest("(str, bool, x: int, args: tuple[bool, ...]) -> bool",
           """
             from typing import Callable, Concatenate, ParamSpec

             P = ParamSpec("P")


             def bar(x: int, *args: bool) -> int: ...


             def add(x: Callable[P, int]) -> Callable[Concatenate[str, bool, P], bool]: ...


             expr = add(bar)  # Should return (__a: str, x: int, *args: bool) -> bool""");
  }

  // PY-49935
  public void testParamSpecConcatenateRemove() {
    doTest("(args: tuple[bool, ...]) -> bool",
           """
             from typing import Callable, Concatenate, ParamSpec

             P = ParamSpec("P")


             def bar(x: int, *args: bool) -> int: ...


             def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


             expr = remove(bar)""");
  }

  // PY-49935
  public void testParamSpecConcatenateTransform() {
    doTest("(str, args: tuple[bool, ...]) -> bool",
           """
             from typing import Callable, Concatenate, ParamSpec

             P = ParamSpec("P")


             def bar(x: int, *args: bool) -> int: ...


             def transform(
                     x: Callable[Concatenate[int, P], int]
             ) -> Callable[Concatenate[str, P], bool]:
                 def inner(s: str, *args: P.args):
                     return True
                 return inner


             expr = transform(bar)""");
  }

  // PY-51329
  public void testBitwiseOrOperatorOverloadUnion() {
    doTest("UnionType",
           """
             class MyMeta(type):
                 def __or__(self, other):
                     return other

             class Foo(metaclass=MyMeta):
                 ...

             expr = Foo | None""");
  }

  // PY-51329
  public void testBitwiseOrOperatorOverloadUnionTypeAlias() {
    doTest("Any",
           """
             class MyMeta(type):
                 def __or__(self, other) -> Any:
                     return other

             class Foo(metaclass=MyMeta):
                 ...

             Alias = Foo | None
             expr: Alias""");
  }

  // PY-51329
  public void testBitwiseOrOperatorOverloadUnionTypeAnnotation() {
    doTest("Any",
           """
             class MyMeta(type):
                 def __or__(self, other) -> Any:
                     return other

             class Foo(metaclass=MyMeta):
                 ...

             expr: Foo | None""");
  }

  // PY-52930
  public void testExceptionGroupInExceptStar() {
    doTest("ExceptionGroup",
           """
             try:
                 raise ExceptionGroup("asdf", [Exception("fdsa")])
             except* Exception as expr:
                 pass
             """);
  }

  /**
   * @see #testRecursiveDictTopDown()
   * @see PyTypeCheckerInspectionTest#testRecursiveDictAttribute()
   */
  public void testRecursiveDictBottomUp() {
    String text = """
      class C:
          def f(self, x):
              self.foo = x
              self.foo = {'foo': self.foo}
              expr = self.foo
      """;
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PyExpression dict = myFixture.findElementByText("{'foo': self.foo}", PyExpression.class);
    assertExpressionType("dict[str, Any]", dict);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    assertExpressionType("dict[str, Any]", expr);
  }

  public void testRecursiveDictTopDown() {
    String text = """
      class C:
          def f(self, x):
              self.foo = x
              self.foo = {'foo': self.foo}
              expr = self.foo
      """;
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    assertExpressionType("dict[str, Any]", expr);
    PyExpression dict = myFixture.findElementByText("{'foo': self.foo}", PyExpression.class);
    assertExpressionType("dict[str, Any]", dict);
  }

  // PY-52656
  public void testDictValuesType() {
    doTest("int",
           """
             d = {'foo': 42}
             for expr in d.values():
                 pass""");
  }

  // PY-76816
  public void testEnumDefinitionUsingEnumSubclass() {
    doTest("Literal[Color.RED]",
           """
             from enum import Enum

             class CustomEnum(Enum):
                 pass
             
             class Color(CustomEnum):
                 RED = 1

             expr = Color.RED
             """);
  }

  // PY-76816
  public void testEnumDefinitionUsingEnumTypeMetaclass() {
    doTest("Literal[Color.RED]",
           """
             from enum import EnumType

             class CustomEnumType(EnumType):
                 pass

             class CustomEnum(metaclass=CustomEnumType):
                 pass

             class Color(CustomEnum):
                 RED = 1

             expr = Color.RED
             """);
  }

  // PY-55734
  public void testEnumValueType() {
    doTest("int",
           """
             from enum import IntEnum, auto

             class State(IntEnum):
                 A = auto()
                 B = auto()

             def foo(arg: State):
                 expr = arg.value
             """);
  }

  // PY-79330
  public void testEnumAutoValueType() {
    doTest("int",
           """
             from enum import auto, Enum
             
             class MyEnum(Enum):
                 FOO = auto()
                 BAR = FOO
             
             def foo(e: MyEnum):
                 expr = e.value""");
  }

  // PY-79330
  public void testEnumAutoValueTypeCustomGenerateNextValue() {
    doTest("str",
           """
             from enum import auto, Enum
             
             class MyEnumBase(Enum):
                 @staticmethod
                 def _generate_next_value_(name: str, start: int, count: int, last_values: list[str]) -> str: ...
             
             class MyEnumDerived(MyEnumBase):
                 FOO = auto()
             
             def foo(e: MyEnumDerived):
                 expr = e.value""");
  }

  // PY-79330
  public void testEnumAutoValueTypeCustomGenerateNextValueMultiFile() {
    doMultiFileTest("str",
                    """
                      from my_enum import MyEnumDerived
                      
                      def foo(e: MyEnumDerived):
                          expr = e.value
                      """);
  }

  // PY-16622
  public void testVariableEnumValueType() {
    doTest("str",
           """
             from enum import Enum


             class IDE(Enum):
                 DS = 'DataSpell'
                 PY = 'PyCharm'


             IDE_TO_CLEAR_SETTINGS_FOR = IDE.PY
             expr = IDE_TO_CLEAR_SETTINGS_FOR.value
             """);
  }

  // PY-16622
  public void testFunctionReturnEnumIntValueType() {
    doTest("int",
           """
             from enum import Enum

             class Fruit(Enum):
                 Apple = 1
                 Banana = 2

             def f():
                 return Fruit.Apple

             res = f()
             expr = res.value
             """);
  }

  public void testEnumMembers() {
    doTest(
      "tuple[() -> int, (x: int) -> None, Literal[Example.A], Literal[Example.B]]",
      """
        from enum import Enum
        
        def func(x: int) -> None: ...
        
        val = 2

        class Example(Enum):
            foo = lambda: 1
            bar = staticmethod(func)
            A = 1
            B = val

        expr = Example.foo, Example.bar, Example.A, Example.B
        """);
  }

  public void testFlagEnumNotExpanded() {
    doTest(
      "MyFlag",
      """
        from enum import Flag
        
        class MyFlag(Flag):
            FLAG1 = 1
            FLAG2 = 2
        
        def foo(f: MyFlag):
            if f is MyFlag.FLAG1:
                pass
            else:
                expr = f
        """
    );
  }

  public void testUnionWithEnumMembers() {
    doTest(
      "Color",
      """
        from enum import Enum
        
        class Color(Enum):
            R = 1
            G = 2
            B = 3
        
        def f(v: str | Color):
            if isinstance(v, str):
                pass
            else:
                expr = v
        """
    );
  }

  public void testEnumMemberNonmember() {
    doTest(
      "tuple[int, Literal[Example.A], Literal[Example.B], Literal[Example.method]]",
           """
             from enum import Enum, member, nonmember
             
             def func(x: int) -> None: ...
             
             class Example(Enum):
                 a = nonmember(1)
                 A = member(lambda: 1)
                 B = member(staticmethod(func))
             
                 @member
                 def method() -> None: ...

             expr = Example.a, Example.A, Example.B, Example.method
             """);
  }

  public void testEnumMemberNonmemberMultiFile() {
    doMultiFileTest("tuple[int, Literal[Example.A], Literal[Example.B], Literal[Example.method]]",
                    """
                      from enum_members import Example
                      
                      expr = Example.a, Example.A, Example.B, Example.method
                      """);
  }

  public void testEnumAuto() {
    doTest("tuple[Literal[Color.RED], Literal[Color.BLUE]]",
           """
             from enum import Enum, auto
             
             class Color(Enum):
                 RED = auto()
                 BLUE = auto()
             
             expr = Color.RED, Color.BLUE
             """);
  }

  public void testEnumAutoMultiFile() {
    doMultiFileTest("tuple[Literal[Color.RED], Literal[Color.BLUE]]",
                    """
                      from color import Color
                      
                      expr = Color.RED, Color.BLUE
                      """);
  }

  public void testEnumMemberAlias() {
    doTest("tuple[Literal[Color.RED], Literal[Color.RED], Literal[Color.RED]]",
           """
             from enum import EnumMeta, member
             
             class Color(metaclass=EnumMeta):
                 RED = 1
             
                 R = RED
                 r = R
             
             expr = Color.RED, Color.R, Color.r
             """);
    doTest("tuple[Literal[Color.foo], Literal[Color.foo], Literal[Color.foo]]",
           """
             from enum import EnumMeta, member
             
             class Color(metaclass=EnumMeta):
                 @member
                 def foo(x: int) -> int:
                     pass
             
                 bar = foo
                 buz = bar
             
             expr = Color.foo, Color.bar, Color.buz
             """);
  }

  public void testEnumMemberAliasMultiFile() {
    doMultiFileTest(
      "tuple[Literal[Color.RED], Literal[Color.RED], Literal[Color.RED], Literal[Color.foo], Literal[Color.foo], Literal[Color.foo]]",
                    """
                      from color import *
                      expr = Color.RED, Color.R, Color.r, Color.foo, Color.bar, Color.buz
                      """);
  }

  // PY-54336
  public void testCyclePreventionDuringGenericsSubstitution() {
    PyTypeVarType typeVarT = new PyTypeVarTypeImpl("T", null);
    PyTypeVarType typeVarV = new PyTypeVarTypeImpl("V", null);
    TypeEvalContext context = TypeEvalContext.codeInsightFallback(myFixture.getProject());
    PyType substituted;

    substituted = PyTypeChecker.substitute(typeVarT, new GenericSubstitutions(Map.of(typeVarT, typeVarT)), context);
    assertEquals(typeVarT, substituted);

    substituted = PyTypeChecker.substitute(typeVarT, new GenericSubstitutions(Map.of(typeVarT, typeVarV, typeVarV, typeVarT)), context);
    assertNull(substituted);

    PyCallableType callable = new PyCallableTypeImpl(List.of(), typeVarT);
    substituted = PyTypeChecker.substitute(callable, new GenericSubstitutions(Map.of(typeVarT, typeVarV, typeVarV, callable)), context);
    PyCallableType substitutedCallable = assertInstanceOf(substituted, PyCallableType.class);
    assertNull(substitutedCallable.getReturnType(context));
  }

  public void testListConstructorCallWithGeneratorExpression() {
    doTest("list[int]",
           "expr = list(int(i) for i in '1')");
  }

  public void testClassDunderNewResult() {
    doTest("C",
           """
             class C(object):
                 def __new__(cls):
                     self = object.__new__(cls)
                     self.foo = 1
                     return self

             expr = C()
             """);
  }

  public void testObjectDunderNewResult() {
    doTest("C",
           """
             class C(object):
                 def __new__(cls):
                     expr = object.__new__(cls)
             """);
  }

  // PY-37678
  public void testDataclassesReplace() {
    doTest("Foo",
           """
             import dataclasses as dc

             @dc.dataclass
             class Foo:
                 x: int
                 y: int

             foo = Foo(1, 2)
             expr = dc.replace(foo, x=3)""");
  }

  // PY-53612
  public void testLiteralStringValidLocations() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("LiteralString",
                   """
                     from typing_extensions import LiteralString
                     def my_function(literal_string: LiteralString) -> LiteralString: ...
                     expr = my_function("42")""")
    );
  }

  // PY-77796
  public void testTypedDictReadOnlyItemType() {
    doTest("str",
           """
             from typing import TypedDict, ReadOnly
             class A(TypedDict):
                 x: ReadOnly[str]
             def f(a: A):
                 expr = a['x']
             """);
    doTest("int",
           """
             from typing import TypedDict, Required, ReadOnly
             class A(TypedDict):
                 x: Required[ReadOnly[int]]
             def f(a: A):
                 expr = a['x']
             """);
    doTest("str",
           """
             from typing import TypedDict, Required, Annotated, ReadOnly
             class A(TypedDict):
                 x: Required[Annotated[ReadOnly[str], 1]]
             def f(a: A):
                 expr = a['x']
             """);
  }

  // PY-53612
  public void testLiteralStringConcatenation() {
    doTest("LiteralString",
           """
             from typing_extensions import LiteralString
             x: LiteralString
             y: LiteralString
             expr = x + y""");
    doTest("str",
           """
             from typing_extensions import LiteralString
             x: LiteralString
             y: str
             expr = x + y""");
    doTest("str",
           """
             from typing_extensions import LiteralString
             x: str
             y: LiteralString
             expr = x + y""");
  }

  // PY-53612
  public void testLiteralStringJoin() {
    doTest("LiteralString",
           """
             from typing_extensions import LiteralString
             x: LiteralString
             xs: list[LiteralString]
             expr = x.join(xs)""");
    doTest("str",
           """
             from typing_extensions import LiteralString
             x: str
             xs: list[LiteralString]
             expr = x.join(xs)""");
    doTest("str",
           """
             from typing_extensions import LiteralString
             x: LiteralString
             xs: list[str]
             expr = x.join(xs)""");
  }

  // PY-53612
  public void testLiteralStringInStringFormat() {
    doTest("LiteralString",
           """
             from typing_extensions import LiteralString
             name: LiteralString = "foo"
             age: LiteralString = "42"
             string: LiteralString = "Hello, {name}. You are {age}"
             expr = string.format(name=name.capitalize(), age=age)""");
    doTest("str",
           """
             from typing_extensions import LiteralString
             name: LiteralString = "foo"
             age = str(42)
             string: LiteralString = "Hello, {name}. You are {age}"
             expr = string.format(name=name.capitalize(), age=age)""");
  }

  public void testTypeGuardList() {
    doTest("list[str]",
           """
             from typing import List
             from typing import TypeGuard
                          
                          
             def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
                 return all(isinstance(x, str) for x in val)
                          
                          
             def func1(val: List[object]):
                 if is_str_list(val):
                     expr = val
             """);
  }

  // PY-75961
  public void testTypeGuardNotAppliedForUnresolvedType() {
    doTest("list[object]",
           """
             from typing import List
             from typing import TypeGuard
                          
             def is_str_list(val: List[object]) -> TypeGuard[Unresolved]:
                 return all(isinstance(x, str) for x in val)                          
                          
             def func1(val: List[object]):
                 if is_str_list(val):
                     expr = val
             """);
  }


  public void testTypeGuardCannotBeReturned() {
    myFixture.configureByText(PythonFileType.INSTANCE, """
             from typing import List
             from typing import TypeGuard
             
             def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
                 return all(isinstance(x, str) for x in val)
                          
                          
             def func1(val: List[object]):
                 return is_str_list(val)
              
             def func2(val):
                 expr = func1(val)                    
             """);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    final Project project = expr.getProject();
    final PsiFile containingFile = expr.getContainingFile();
    final PyType type = TypeEvalContext.userInitiated(project, containingFile).getType(expr);
    assertFalse("type is instance of PyNarrowedType ", type instanceof PyNarrowedType);
  }

  public void testTypeGuardResultIsAssigned()  {
    doTest("list[str]",
           """
             from typing import List
             from typing import TypeGuard
                                                    
             def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
                 return all(isinstance(x, str) for x in val)
                          
                          
             def func1(x, val: List[object]):
                 b = is_str_list(val)
                 if x and b:
                     expr = val
             """);
  }

  public void testTypeGuardResultIsAssignedButValIsReassigned() {
    doTest("int",
           """
             from typing import List
             from typing import TypeGuard
             
             def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
                 return all(isinstance(x, str) for x in val)
             
             
             def func1(x, val: List[object]):
                 b = is_str_list(val)
                 val = 1
                 if x and b:
                     expr = val
             """);
  }


  public void testTypeGuardResultIsAssignedButValIsReassignedSometimes() {
    doTest("list[str] | int",
           """
             from typing import List
             from typing import TypeGuard
             
             def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
                 return all(isinstance(x, str) for x in val)
             
             
             def func1(x, val: List[object]):
                 b = is_str_list(val)
                 if x:
                     val = 1
                 if b:
                     expr = val
             """);
  }



  public void testTypeGuardPresentation() {
    doTest("TypeGuard[list[str]]",
           """
             from typing import List
             from typing import TypeGuard


             def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
                 return all(isinstance(x, str) for x in val)


             def func1(val: List[object]):
                 expr = is_str_list(val)
             """);
  }

  public void testTypeIsPresentation() {
    doTest("TypeIs[list[str]]",
           """
             from typing import List
             from typing_extensions import TypeIs


             def is_str_list(val: List[object]) -> TypeIs[List[str]]:
                 return all(isinstance(x, str) for x in val)


             def func1(val: List[object]):
                 expr = is_str_list(val)
             """);
  }

  public void testTypeGuardIsErasedOnReturn() {
    doTest("bool",
           """
             from typing import List
             from typing_extensions import TypeIs

             def is_str_list(val: List[object]) -> TypeIs[List[str]]:
                 return all(isinstance(x, str) for x in val)

             def func1(val: List[object]):
                 return is_str_list(val)
             
             expr = func1([])
             """);
  }

  public void testTypeAliasesWithTypeIs() {
    doTest("list[str]", """
      from typing import List
      from typing_extensions import TypeIs
      
      MyTypeIs = TypeIs[List[str]]

      def is_str_list(val: List[object]) -> MyTypeIs:
          return all(isinstance(x, str) for x in val)

      def func1(val: List[object]):
          if is_str_list(val):
              expr = val
      """);
  }

  public void testTypeAliasWithGenericTypeIs() {
    doTest("list[str]", """
      from typing import List
      from typing_extensions import TypeIs
      
      type MyTypeIs[T] = TypeIs[T]

      def is_str_list(val: List[object]) -> MyTypeIs[List[str]]:
          return all(isinstance(x, str) for x in val)

      def func1(val: List[object]):
          if is_str_list(val):
              expr = val
      """);
  }

  public void testTypeIsWithGenerics() {
    doTest("tuple[str, str]", """
      from typing_extensions import TypeIs
      from typing import TypeVar
      
      T = TypeVar("T")
      
      def is_two_element_tuple(val: tuple[T, ...]) -> TypeIs[tuple[T, T]]:
          return len(val) == 2
      
      
      def func7(names: tuple[str, ...]):
          if is_two_element_tuple(names):
              expr = names
      """);
  }


  public void testTypeGuardListInStringLiteral() {
    doTest("list[str]",
           """
             from typing import List
             from typing import TypeGuard
                          
                          
             def is_str_list(val: List[object]) -> "TypeGuard[List[str]]":
                 return all(isinstance(x, str) for x in val)
                          
                          
             def func1(val: List[object]):
                 if is_str_list(val):
                     expr = val
             """);
  }


  public void testTypeGuardListTypeIsNotChanged() {
    doTest("list[object]",
           """
             from typing import List
             from typing import TypeGuard
                          
                          
             def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
                 return all(isinstance(x, str) for x in val)
                          
                          
             def func1(val: List[object]):
                 if is_str_list(val):
                     pass
                 else:
                     expr = val
             """);
  }

  public void testTypeGuardListNegation() {
    doTest("list[str]",
           """
             from typing import List
             from typing import TypeGuard
                          
                          
             def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
                 return all(isinstance(x, str) for x in val)
                          
                          
             def func1(val: List[object]):
                 if not is_str_list(val):
                     pass
                 else:
                     expr = val
             """);
  }

  // PY-62078
  public void ignoreTestTypeGuardAnnotation() {
    doTest("list[str]",
           """
             from typing import List
             from typing import TypeGuard
                          
                          
             def is_str_list(val):
                 # type: (List[object]) -> TypeGuard[List[str]]
                 return all(isinstance(x, str) for x in val)
                          
                          
             def func1(val: List[object]):
                 if not is_str_list(val):
                     pass
                 else:
                     expr = val
             """);
  }

  public void testTypeGuardDidntChanged() {
    doTest("list[object]",
           """
             from typing import List
             from typing import TypeGuard
                          
                          
             def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
                 return all(isinstance(x, str) for x in val)
                          
                          
             def func1(val: List[object]):
                 if not is_str_list(val):
                     expr = val
                 else:
                     pass
             """);
  }

  public void testTypeGuardDoubleCheck() {
    doTest("Person",
           """
             from typing import TypeGuard
             class Person(TypedDict):
                 name: str
                 age: int
                                
             def is_person(val: dict) -> TypeGuard[Person]:
                 try:
                     return isinstance(val["name"], str) and isinstance(val["age"], int)
                 except KeyError:
                     return False
                                
                                
             def print_age(val: dict, val2: dict):
                 if is_person(val) and is_person(val2):
                     expr = val
                 else:
                     print("Not a person!")""");
  }

  public void testTypeIsInCallable() {
    doTest("str", """
      from typing import Callable
      from typing import assert_type
      from typing_extensions import TypeIs
     
      def takes_narrower(x: int | str, narrower: Callable[[object], TypeIs[int]]):
          if narrower(x):
              pass
          else:
              expr = x
     """);
  }

  public void testTypeGuardDoubleCheckNegation() {
    doTest("Person",
           """
             from typing import TypeGuard
             class Person(TypedDict):
                 name: str
                 age: int
                                
                                
             def is_person(val: dict) -> TypeGuard[Person]:
                 try:
                     return isinstance(val["name"], str) and isinstance(val["age"], int)
                 except KeyError:
                     return False
                                
                                
             def print_age(val: dict, val2: dict):
                 if not is_person(val) or not is_person(val2):
                     print("Not a person!");
                 else:
                     expr = val
                     """);
  }

  public void testFailedTypeGuardCheckDoesntAffectOriginalType() {
    doTest("list[int] | list[str]",
           """
             from typing import List
             from typing import TypeGuard
                          
             def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
                 return all(isinstance(x, str) for x in val)
                          
                          
             def func1(val: List[int] | List[str]):
                 if not is_str_list(val):
                     expr = val
                 else:
                     pass
             """);
  }

  public void testFailedTypeIsCheckDoesAffectOriginalType() {
    doTest("list[int]",
           """
             from typing import List
             from typing_extensions import TypeIs
             
             def is_str_list(val: List[object]) -> TypeIs[List[str]]:
                 return all(isinstance(x, str) for x in val)
             
             def func1(val: List[int] | List[str]):
                 if not is_str_list(val):
                     expr = val
                 else:
                     pass
             """);
  }

  public void testHandleGenericReturnType() {
    doTest("list[str]", """
      from typing import List
      
      def create_list_of_type[T](item: T, count: int) -> List[T]:
          return [item] * count
      
      expr = create_list_of_type("foo", 3)
      """);
  }

  public void testHandleGenericWithAliasesReturnType() {
    doTest("int | None", """
      from typing import Dict, TypeAlias, TypeVar
      
      V = TypeVar("V")
      
      StringDict = Dict[str, V]
      
      def create_dict_of_type[T](item: T,) -> StringDict[T]:
          return {"foo": item}
      
      
      dict = create_dict_of_type(23)
      
      expr = dict.get("foo")
      """);
  }

  public void testNoReturn() {
    doTest("Bar",
           """
             from typing import NoReturn
             
             class Foo:
                 def stop(self) -> NoReturn:
                     raise RuntimeError('no way')
             
             class Bar:
                 ...
             
             def foo(x):
                 f = Foo()
                 if not isinstance(x, Bar):
                     f.stop()
                 expr = x # expr is Bar, not Union[Bar, Any]
             """);
  }

  public void testTypeIs1() {
    doTest("str", """
             from typing import Any, Callable, Literal, Mapping, Sequence, TypeVar, Union
             from typing_extensions import TypeIs
             
             
             def is_str1(val: Union[str, int]) -> TypeIs[str]:
                 return isinstance(val, str)
             
             
             def func1(val: Union[str, int]):
                 if is_str1(val):
                     expr = val
                 else:
                     pass
             """);
  }

  public void testTypeIs2() {
    doTest("int", """
             from typing import Any, Callable, Literal, Mapping, Sequence, TypeVar, Union
             from typing_extensions import TypeIs
             
             
             def is_str1(val: Union[str, int]) -> TypeIs[str]:
                 return isinstance(val, str)
             
             
             def func1(val: Union[str, int]):
                 if is_str1(val):
                     pass
                 else:
                     expr = val
             """);
  }

  public void testTypeIs3() {
    doTest("list[str] | list[int]", """
      from typing import Any, Callable, Literal, Mapping, Sequence, TypeVar, Union
      from typing_extensions import TypeIs
      
      def is_list(val: object) -> TypeIs[list[Any]]:
          return isinstance(val, list)
      
      
      def func3(val: dict[str, str] | list[str] | list[int] | Sequence[int]):
          if is_list(val):
              expr = val
          else:
              pass
      """);
  }

  // PY-61137
  public void testLiteralStringIsNotInferredWithoutExplicitAnnotation() {
    doTest("list[str]",
           """
             expr = ['1' + '2']""");
    doTest("list[str]",
           """
             from typing import TypeVar
             T = TypeVar("T")
             def same_type(x: T, y: T) -> T:
                 pass
             s: str
             expr = same_type(['foo'], [s])""");
    doTest("list[str]",
           "expr = ['foo', 'bar']");
    doTest("deque[str]",
           """
             from collections import deque
             expr = deque(['foo', 'bar'])""");
    doTest("LiteralString",
           "expr = '1' + '2'");
    doTest("LiteralString",
           "expr = '%s' % ('a')");
  }

  // PY-27708
  public void testDictCompExpressionWithGenerics() {
    doTest("dict[str, (Any) -> Any]",
           """
              from typing import Callable, Dict, Any

              def test(x: Dict[str, Callable[[Any], Any]]):
                  y = {k: v for k, v in x.items()}
                  expr = y
                    
             """);
  }

  public void testDictFromKWArgs() {
    doTest("dict[str, Any]",
           """
             def test(**kwargs):
                 expr = {k: v for k, v in kwargs.items()}
             """);
  }


  // PY-27708
  public void testSetCompExpressionWithGenerics() {
    doTest("set[(Any) -> Any]",
           """
             from typing import Callable, Any, Set
                                
             def test(x: Set[Callable[[Any], Any]]):
                 y = {k for k in x}
                 expr = y
             """);
  }

  public void testEnumerateType() {
    doTest("tuple[int, int]",
           """
             a: list[int] = [1, 2, 3]
             for expr in enumerate(a):
                 pass
             """);
  }

  // PY-61883
  public void testParamSpecExampleWithPEP695Syntax() {
    doTest("(a: str, b: bool) -> str",
           """
             from typing import Callable

             def changes_return_type_to_str[**P](x: Callable[P, int]) -> Callable[P, str]: ...

             def returns_int(a: str, b: bool) -> int:
                 return 42
                 
             expr = changes_return_type_to_str(returns_int)""");
  }

  // PY-61883
  public void testParamSpecInImportedFileWithPEP695Syntax() {
    doMultiFileTest("(a: str, b: bool) -> str",
                    """
                      from a import changes_return_type_to_str
                            
                      def returns_int(a: str, b: bool) -> int:
                          return 42

                      expr = changes_return_type_to_str(returns_int)
                      """);
  }

  // PY-61883
  public void testParamSpecConcatenateTransformWithPEP695Syntax() {
    doTest("(str, args: tuple[bool, ...]) -> bool",
           """
            from typing import Callable, Concatenate
            
            def bar(x: int, *args: bool) -> int: ...
            
            
            def transform[**P](
                    x: Callable[Concatenate[int, P], int]
            ) -> Callable[Concatenate[str, P], bool]:
                def inner(s: str, *args: P.args):
                    return True
            
                return inner
            
            
            expr = transform(bar)""");
  }

  // PY-61883
  public void testParamSpecUserGenericClassWithPEP695Syntax() {
    doTest("Y[int, [q: int, p: str, r: bool]]",
           """
             from typing import Callable

             class Y[U, **P]:
                 f: Callable[P, str]
                 attr: U

                 def __init__(self, f: Callable[P, str], attr: U) -> None:
                     self.f = f
                     self.attr = attr


             def a(q: int, p: str, r: bool) -> str: ...


             expr = Y(a, 1)
             """);
  }

  // PY-64474
  public void testTupleElementAccessedWithNegativeIndex() {
    doTest("bool",
           """
             xs = (1, True, "foo")
             expr = xs[-2]
             """);
  }

  // PY-64474
  public void testTupleElementAccessedWithOutOfBoundIndex() {
    doTest("tuple[Any, Any]",
           """
             xs = (1, True, "foo")
             expr = xs[-10], xs[10]
             """);
  }

  public void testHomogenousTupleElementAccessedWithOutOfBoundIndex() {
    doTest("tuple[str, str]",
           """
             xs: tuple[str, ...] = tuple(['foo'])
             expr = xs[-10], xs[10]
             """);
  }

  // PY-55044
  public void testTypedDictKwargs() {
    doTest("Movie",
           """
             from typing import TypedDict, Unpack
             class Movie(TypedDict):
                 name: str
                 year: int
             def foo(**x: Unpack[Movie]):
                 expr = x
             """);
  }

  // PY-55044
  public void testKwargsWithUnpackedClassTypeInAnnotation() {
    doTest("dict[str, Any]",
           """
             from typing import Unpack
             class Movie:
                 pass
             def foo(**x: Unpack[Movie]):
                 expr = x
             """);
  }

  // PY-34617
  public void testTopLevelFunctionUnderVersionCheck() {
    runWithLanguageLevel(LanguageLevel.PYTHON310, () -> {
      doMultiFileTest("str",
                      """
                        from mod import foo
                        expr = foo()
                        """);
    });
  }

  // PY-34617
  public void testClassMethodUnderVersionCheck() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> {
      doMultiFileTest("float",
                      """
                        from mod import Foo
                        expr = Foo().foo()
                        """);
    });
  }


  // PY-73958
  public void testNoStackOverflow() {
    doTest("Foo", """
            class Foo:
                def foo(self):
                    pass

            xxx = Foo()

            """ + "xxx.foo()\n".repeat(1000) + """
            expr = xxx
            """);
  }

  // PY-26184
  public void testGenericTypeFromDescriptor() {
    doTest("list", """
      import typing

      class MyDescriptor[T]:
          def __init__(self, requested_type: typing.Type[T]):
              self.requested_type = requested_type
          def __get__(self, instance: typing.Any, owner: typing.Any) -> T:
              raise Exception("Not implemented")
      
      class Test:
          member = MyDescriptor(list)
          def foo(self):
              test = self.member
              expr = test
      """);
  }

  // PY-26184
  public void testGenericTypeFromDescriptorWithTypeAnnotationOnly() {
    doTest("list", """
      from typing import Type, Any
      
      class MyDescriptor[T]:
          def __get__(self, instance: typing.Any, owner: typing.Any) -> T:
              raise Exception("Not implemented")
     
      class Test:
          member: MyDescriptor[list]
          def foo(self):
              test = self.member
              expr = test
      """);
  }

  // PY-26184
  public void testGenericTypeFromDescriptorWithTypeAnnotationPriority() {
    doTest("list", """
      from typing import Type, Any
      
      class MyDescriptor[T]:
          def __init__(self, requested_type: Type[T]):
              self.requested_type = requested_type
          def __get__(self, instance: Any, owner: Any) -> T:
              raise Exception("Not implemented")
      
      class Test:
          member: MyDescriptor[list] = MyDescriptor(str)
          def foo(self):
              test = self.member
              expr = test
      """);
  }

  // PY-26184
  public void testGenericDescriptorAccessViaInstance() {
    doTest("int", """
      from typing import Optional, Any, overload, Union

      class MyDescriptor[T]:
          @overload
          def __get__(self, instance: None, owner: Any) -> str: # access via class
              ...
          @overload
          def __get__(self, instance: object, owner: Any) -> T: # access via instance
              ...
          def __get__(self, instance: Optional[object], owner: Any) -> Union[str, T]:
              ...
     
      class Foo():
          x = MyDescriptor[int]()
      
      foo = Foo()
      expr = foo.x
      """);
  }

  // PY-26184
  public void testGenericDescriptorAccessViaInstanceNoMatchingOverloads() {
    doTest("Any", """
      from typing import Any, overload, Union
      
      class MyDescriptor[T]:
          @overload
          def __get__(self, instance: None, owner: Any) -> T: # access via class
              ...
          def __get__(self, instance: "Bar", owner: Any) -> Union[str, T]:
              ...
      
      class Foo():
          x = MyDescriptor[int]()
      
      class Bar(Foo):
          x = MyDescriptor[int]()
      
      expr = Foo().x
      """);
  }

  // PY-26184
  public void testGenericDescriptorAccessViaInstanceReturnsExplicitAny() {
    doTest("Any", """
      from typing import Optional, Any, overload, Union

      class MyDescriptor[T]:
          @overload
          def __get__(self, instance: None, owner: Any) -> str: # access via class
              ...
          @overload
          def __get__(self, instance: object, owner: Any) -> Any: # access via instance
              ...
          def __get__(self, instance: Optional[object], owner: Any) -> Union[str, T]:
              ...
     
      class Foo():
          x = MyDescriptor[int]()
      
      foo = Foo()
      expr = foo.x
      """);
  }

  // PY-26184
  public void testGenericDescriptorAccessViaClass() {
    doTest("int", """
      from typing import Optional, Any, overload, Union

      class MyDescriptor[T]:
          @overload
          def __get__(self, instance: None, owner: Any) -> T: # access via class
              ...
          @overload
          def __get__(self, instance: object, owner: Any) -> str: # access via instance
              ...
          def __get__(self, instance: Optional[object], owner: Any) -> Union[str, T]:
              ...
     
      class Foo():
          x = MyDescriptor[int]()
      
      expr = Foo.x
      """);
  }

  // PY-26184
  public void testGenericDescriptorAccessViaClassReturnsExplicitAny() {
    doTest("Any", """
      from typing import Optional, Any, overload, Union

      class MyDescriptor[T]:
          @overload
          def __get__(self, instance: None, owner: Any) -> Any: # access via class
              ...
          @overload
          def __get__(self, instance: object, owner: Any) -> str: # access via instance
              ...
          def __get__(self, instance: Optional[object], owner: Any) -> Union[str, T]:
              ...
     
      class Foo():
          x = MyDescriptor[int]()
      
      expr = Foo.x
      """);
  }

  // PY-26184
  public void testGenericDescriptorAccessViaClassReturnsNothing() {
    doTest("None", """
      from typing import Optional, Any, overload, Union

      class MyDescriptor[T]:
          @overload
          def __get__(self, instance: None, owner: Any): # access via class
              ...
          @overload
          def __get__(self, instance: object, owner: Any) -> T: # access via instance
              ...
          def __get__(self, instance: Optional[object], owner: Any) -> Union[str, T]:
              ...
     
      class Foo():
          x = MyDescriptor[int]()
      
      expr = Foo.x
      """);
  }

  // PY-26184
  public void testGenericTypeFromParameterizedOnInheritanceDescriptorWithTypeAnnotationOnly() {
    doTest("str", """
      from typing import Any
      
      class MyDescriptor[T]:
          def __get__(self, instance: Any, owner: Any) -> T:
              ...
      
      class StrDescriptor(MyDescriptor[str]):
          pass
      
      class Test:
          member: StrDescriptor
      
          def foo(self):
              test = self.member
              expr = test
      """);
  }

  // PY-26184
  public void testGenericTypeFromDescriptorDefinedWithTypeAnnotationInExternalFileAccessViaInstance() {
    doMultiFileTest("str", """
      from a import Test

      test = Test()
      expr = test.member
      """);
  }

  // PY-71748
  public void testDictDunderEqAppliedFromLeftToRightByDefault() {
    doTest("int", """
      class A:
        def __eq__(self, other: Any) -> int: ...
      
      class B:
        def __eq__(self, other: Any) -> str: ...
      
      a = A()
      b = B()
      expr = a == b
      """);
  }

  // PY-71748
  public void testDictDunderNeAppliedFromLeftToRightByDefault() {
    doTest("int", """
      class A:
        def __ne__(self, other: Any) -> int: ...
      
      class B:
        def __ne__(self, other: Any) -> str: ...
      
      a = A()
      b = B()
      expr = a != b
      """);
  }

  // PY-60968
  public void testCsvDictReaderIteratorType() {
    doTest("list[dict[str | Any, str | Any]]", """
          import csv
          with open("file.csv") as f:
              reader = csv.DictReader(f)
              expr = [line for line in reader]
      """);
  }

  public void testLiteralAssignmentInImportedFile() {
    doMultiFileTest("int", """
      from m import *
      expr = foo
      """);
  }

  // PY-77937
  public void testListOfLiterals() {
    doTest("list[int]", """
      from typing import Literal
      
      num1: Literal[1] = 1
      num2: Literal[2] = 2
      expr = [num1, num2]
      """);

    doTest("list[int | str]", """
      from typing import Literal
      
      e1: Literal[1] = 1
      e2: Literal["abc"] = "abc"
      expr = [e1, e2]
      """);

    doTest("list[int | str]", """
      from typing import Literal, LiteralString
      
      e: Literal[1, "ab"] | LiteralString | Literal["x"] = "abb"
      expr = [e]
      """);

    doTest("list[Direction]", """
      from enum import Enum
      
      class Direction(Enum):
          NORTH = "N"
          SOUTH = "S"
          EAST = "E"
          WEST = "W"
      
      expr = [Direction.NORTH, Direction.WEST]
      """);
  }

  // PY-77937
  public void testSetOfLiterals() {
    doTest("set[int]", """
      from typing import Literal
      
      num1: Literal[1] = 1
      num2: Literal[2] = 2
      expr = {num1, num2}
      """);

    doTest("set[int | str]", """
      from typing import Literal
      
      e1: Literal[1] = 1
      e2: Literal["abc"] = "abc"
      expr = {e1, e2}
      """);

    doTest("set[int | str]", """
      from typing import Literal, LiteralString
      
      e: Literal[1, "ab"] | LiteralString | Literal["x"] = "abb"
      expr = {e}
      """);

    doTest("set[Direction]", """
      from enum import Enum
      
      class Direction(Enum):
          NORTH = "N"
          SOUTH = "S"
          EAST = "E"
          WEST = "W"
      
      expr = {Direction.NORTH, Direction.WEST}
      """);
  }

  // PY-77937
  public void testDictOfLiterals() {
    doTest("dict[int, str]", """
      from typing import Literal
      
      k1: Literal[1] = 1
      v1: Literal["2"] = "1"
      k2: Literal[2] = 2
      v2: Literal["2"] = "2"
      expr = {k1: v1, k2: v2}
      """);

    doTest("dict[int | str, str | bool]", """
      from typing import Literal, LiteralString
      
      k1: Literal[1] = 1
      v1: Literal["ab"] = "ab"
      k2: LiteralString = "k2"
      v2: Literal[True] = True
      expr = { k1: v1, k2: v2 }
      """);

    doTest("dict[int | str, int | str]", """
      from typing import Literal, LiteralString
      
      k: Literal[1, "ab"] | LiteralString | Literal["x"] = "abb"
      v: Literal[1, "ab"] | LiteralString | Literal["x"] = 1
      expr = {k: v}
      """);

    doTest("dict[Direction, Direction]", """
      from enum import Enum
      
      class Direction(Enum):
          NORTH = "N"
          SOUTH = "S"
          EAST = "E"
          WEST = "W"
      
      expr = {Direction.NORTH: Direction.SOUTH, Direction.WEST: Direction.EAST}
      """);
  }

  // PY-78125
  public void testDictOfLiteralsWithStringOnlyKeys() {
    doTest("dict[str, int | str]", """
      from typing import Literal

      v: Literal[1, "ab"] = 1
      expr = {"abb": v}
      """);

    doTest("dict[str, int | str]", """
      from typing import Literal

      k = "abb"
      v: Literal[1, "ab"] = 1
      expr = {k: v}
      """);

    doTest("dict[str, int | str]", """
      from typing import Literal

      k: Literal["abb"] = "abb"
      v: Literal[1, "ab"] = 1
      expr = {k: v}
      """);

    doTest("dict[str, int | str]", """
      from typing import Literal, LiteralString

      k: LiteralString = "k"
      v: Literal[1, "ab"] = 1
      expr = {k: v}
      """);
  }

  public void testFunctionReturnsNone() {
    doTest("(p: Any) -> None", """
      def foo(p):
          assert p
      expr = foo
      """);
  }

  // PY-78044
  public void testPathlibIterdir() {
    doTest("Generator[Path, None, None]", """
      import pathlib
      expr = pathlib.Path("").iterdir()
      """);
  }

  // PY-78653
  public void testTypeVarConstraints() {
    doTest("tuple[str, str]", """
      from typing import TypeVar
      
      AnyStr = TypeVar('AnyStr', str, bytes)
      
      def concat(x: AnyStr, y: AnyStr) -> AnyStr:
          return x + y
      
      class MyStr(str): ...
      
      s1 = concat(MyStr('apple'), MyStr('pie'))
      s2 = concat(MyStr('apple'), 'pie')
      expr = (s1, s2)
      """);
    doTest("tuple[str, str]", """
      def concat[AnyStr: (str, bytes)](x: AnyStr, y: AnyStr) -> AnyStr:
          return x + y
      
      class MyStr(str): ...
      
      s1 = concat(MyStr('apple'), MyStr('pie'))
      s2 = concat(MyStr('apple'), 'pie')
      expr = (s1, s2)
      """);
  }

  public void testShadowingReturnInsideFinally() {
    doTest("str", """
      def f():
          try:
              return 42
          finally:
              return "foo"
      expr = f()
      """);
  }

  public void testNonShadowingReturnInsideFinally() {
    doTest("str | int", """
      def f(p):
          try:
              return 42
          finally:
              if p:
                  return "foo"
      expr = f()
      """);
  }

  public void testReturnInsideExceptElse() {
    doTest("str | bool", """
      def f(p):
          try:
              e1()
          except Exception:
              return "foo"
          else:
              return True
          finally:
              pass
      expr = f()
      """);
  }

  public void testMetaclassDunderCallReturnTypeIncompatibleWithClassBeingConstructed() {
    doTest("object", """
      from typing import Self
      
      
      class Meta(type):
          def call(cls, p) -> object: ...
      
          __call__ = call
      
      
      class MyClass(metaclass=Meta):
          def __new__(cls, p) -> Self: ...
      
      
      expr = MyClass(1)
      """);
  }

  public void testMetaclassNotAnnotatedDunderCall() {
    doTest("MyClass", """
      from typing import Self
      
      
      class Meta(type):
          def __call__(cls, p: int): ...
      
      
      class MyClass(metaclass=Meta):
          def __new__(cls, p: int) -> Self: ...
      
      
      expr = MyClass(1)
      """);
  }

  public void testMetaclassGenericDunderCallReturnTypeCompatibleWithClassBeingConstructed() {
    doTest("MyClass", """
      from typing import Self
      
      
      class Meta(type):
          def __call__[T](cls: type[T], *args, **kwargs) -> T: ...
      
      
      class MyClass(metaclass=Meta):
          def __new__(cls, p) -> Self: ...
      
      
      expr = MyClass(1)
      """);
  }

  public void testMetaclassGenericDunderCallReturnTypeIncompatibleWithClassBeingConstructed() {
    doTest("int", """
      from typing import Self
      
      
      class Meta(type):
          def __call__[T](cls, x: T) -> T: ...
      
      
      class MyClass(metaclass=Meta):
          def __new__(cls, x) -> Self: ...
      
      
      expr = MyClass(1)
      """);
  }

  public void testMetaclassDunderCallReturnTypeCompatibleWithClassBeingConstructed() {
    doTest("Base", """
      from typing import Any, Self


      class Meta(type):
          def __call__(self, *args: Any, **kwds: Any) -> 'Derived': ...


      class Base(metaclass=Meta):
          def __new__(cls, *args: Any, **kwds: Any) -> Self: ...


      class Derived(Base):
          ...


      expr = Base()
      """);
  }

  // PY-79967
  public void testTypeOfTemplateStringInferredAsTemplateForPython314() {
    runWithLanguageLevel(LanguageLevel.PYTHON314, () -> {
      doTest("Template", """
        expr = t"template string"
        """);
    });
  }

  // PY-79967
  public void testTypeOfTemplateStringInferredAsStrForPython314() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      doTest("str", """
        expr = t"template string"
        """);
    });
  }

  // PY-79967
  public void testInterpolationExpressionTypeFromTemplateString() {
    runWithLanguageLevel(LanguageLevel.PYTHON314, () -> {
      doTest("str", """
        name = "John"
        expr = t"Hello, {name}!".interpolations[0].expression
        """);
    });
  }

  public void testSliceExpression() {
    doTest("int", """
      from typing import overload
      
      class A[T]:
          @overload
          def __getitem__(self, s: str) -> str: ...
      
          @overload
          def __getitem__(self, s: slice) -> T: ...
      
          def __getitem__(self, s: str | slice) -> str | T: ...
      
      expr = A[int]()[0:2]
      """);
  }

  // PY-80436
  public void testEllipsis() {
    doTest("EllipsisType", "expr = ...");
    doTest("EllipsisType", "expr = Ellipsis");
  }

  // PY-80166, PY-80167
  public void testVarianceObtainedFromTypeVarDeclaration() {
    doTestTypeVarVariance(PyTypeVarType.Variance.INVARIANT, """
      from typing import TypeVar
      T = TypeVar("T")
      expr: T
      """);
    doTestTypeVarVariance(PyTypeVarType.Variance.COVARIANT, """
      from typing import TypeVar
      T_co = TypeVar("T_co", covariant=True)
      expr: T_co
      """);
    doTestTypeVarVariance(PyTypeVarType.Variance.CONTRAVARIANT, """
      from typing import TypeVar
      T_contra = TypeVar("T_contra", contravariant=True)
      expr: T_contra
      """);
    doTestTypeVarVariance(PyTypeVarType.Variance.INFER_VARIANCE, """
      from typing import TypeVar
      T_inf = TypeVar("T_inf", infer_variance=True)
      expr: T_inf
      """);
    doTestTypeVarVariance(PyTypeVarType.Variance.INVARIANT, """
      from typing import TypeVar
      T_wrong = TypeVar("T_wrong", covariant=True, contravariant=True)
      expr: T_wrong
      """);
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doTestTypeVarVariance(PyTypeVarType.Variance.INFER_VARIANCE, """
      def foo[T]():
        expr: T
      """);
    });
  }

  // PY-37755
  public void testNonLocalType() {
    doTest("bool",
           """
             def fun():
                 expr = True

                 def nuf():
                     nonlocal expr
                     expr""");

    doTest("bool",
           """
             a = []

             def fun():
                 a = True

                 def nuf():
                     nonlocal a
                     expr = a""");

    doTest("bool | int",
           """
             a = []

             def fun():
                 if True:
                     a = True
                 else:
                     a = 5

                 def nuf():
                     nonlocal a
                     expr = a""");

    // PY-82115
    doTest("str",
           """
             def outer1():
                 s = "aba"
             
                 def outer2():
                     def inner1():
                         nonlocal s
                         expr = s
             
                     def inner2():
                         global s
                         s = 1
             """);
  }

  // PY-75679
  public void testSelfSubstitutedWithGenericQualifierType() {
    doTest("Derived[int]", """
      from typing import Self, Generic, TypeVar
      T = TypeVar('T')
      class Base1(Generic[T]):
          def foo(self) -> Self:
              return self
      
      class Base2:
          def bar(self) -> Self:
              return self
      
      class Derived(Base1[T], Base2): ...

      d = Derived[int]()
      expr = d.bar().foo().bar().foo()
      """);
  }

  // PY-75679
  public void testSelfSubstitutedWithQualifierType() {
    doTest("B", """
      from typing import Self
      
      class A[T]:
          def f(self) -> Self: ...
      
      class B(A[int]): ...
      
      expr = B().f()
      """);
  }

  private void doTest(final String expectedType, final String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    assertExpressionType(expectedType, expr);
  }

  private void assertExpressionType(@NotNull String expectedType, @NotNull PyExpression expr) {
    final Project project = expr.getProject();
    final PsiFile containingFile = expr.getContainingFile();
    assertType(expectedType, expr, TypeEvalContext.codeAnalysis(project, containingFile));
    assertProjectFilesNotParsed(containingFile);
    assertType(expectedType, expr, TypeEvalContext.userInitiated(project, containingFile));
  }

  private void doTestTypeVarVariance(PyTypeVarType.Variance variance, String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    assertNotNull(expr);
    PyType type = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile()).getType(expr);
    assertInstanceOf(type, PyTypeVarType.class);
    assertEquals(variance, ((PyTypeVarType)type).getVariance());
  }

  private void doMultiFileTest(@NotNull String expectedType, @NotNull String text) {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    doTest(expectedType, text);
  }
}
