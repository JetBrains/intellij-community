// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyTypeCheckerInspectionTest;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class Py3TypeTest extends PyTestCase {
  public static final String TEST_DIRECTORY = "/types/";

  // PY-6702
  public void testYieldFromType() {
    doTest("str | int | float",
           "def subgen():\n" +
           "    for i in [1, 2, 3]:\n" +
           "        yield i\n" +
           "\n" +
           "def gen():\n" +
           "    yield 'foo'\n" +
           "    yield from subgen()\n" +
           "    yield 3.14\n" +
           "\n" +
           "for expr in gen():\n" +
           "    pass\n");
  }

  // PY-12944
  public void testYieldFromReturnType() {
    doTest("None",
           "def a():\n" +
           "    yield 1\n" +
           "    return 'a'\n" +
           "\n" +
           "y = [1, 2, 3]\n" +
           "\n" +
           "def b():\n" +
           "    expr = yield from y\n" +
           "    return expr\n");
    doTest("str",
           "def a():\n" +
           "    yield 1\n" +
           "    return 'a'\n" +
           "\n" +
           "def b():\n" +
           "    expr = yield from a()\n" +
           "    return expr\n");
    doTest("int",
           "def g():\n" +
           "    yield 1\n" +
           "    return 'abc'\n" +
           "\n" +
           "def f()\n" +
           "    x = yield from g()\n" +
           "\n" +
           "for expr in f():\n" +
           "    pass");
  }

  public void testYieldFromHomogeneousTuple() {
    doTest("str",
           "import typing\n" +
           "def get_tuple() -> typing.Tuple[str, ...]:\n" +
           "    pass\n" +
           "def gen()\n" +
           "    yield from get_tuple()\n" +
           "for expr in gen():" +
           "    pass");
  }

  public void testYieldFromHeterogeneousTuple() {
    doTest("int | str",
           "import typing\n" +
           "def get_tuple() -> typing.Tuple[int, int, str]:\n" +
           "    pass\n" +
           "def gen()\n" +
           "    yield from get_tuple()\n" +
           "for expr in gen():" +
           "    pass");
  }

  public void testYieldFromUnknownTuple() {
    doTest("Any",
           "def get_tuple() -> tuple:\n" +
           "    pass\n" +
           "def gen()\n" +
           "    yield from get_tuple()\n" +
           "for expr in gen():" +
           "    pass");
  }

  public void testYieldFromUnknownList() {
    doTest("Any",
           "def get_list() -> list:\n" +
           "    pass\n" +
           "def gen()\n" +
           "    yield from get_list()\n" +
           "for expr in gen():" +
           "    pass");
  }

  public void testYieldFromUnknownDict() {
    doTest("Any",
           "def get_dict() -> dict:\n" +
           "    pass\n" +
           "def gen()\n" +
           "    yield from get_dict()\n" +
           "for expr in gen():" +
           "    pass");
  }

  public void testYieldFromUnknownSet() {
    doTest("Any",
           "def get_set() -> set:\n" +
           "    pass\n" +
           "def gen()\n" +
           "    yield from get_set()\n" +
           "for expr in gen():" +
           "    pass");
  }

  public void testAwaitAwaitable() {
    doTest("int",
           "class C:\n" +
           "    def __await__(self):\n" +
           "        yield 'foo'\n" +
           "        return 0\n" +
           "\n" +
           "async def foo():\n" +
           "    c = C()\n" +
           "    expr = await c\n");
  }

  public void testAsyncDefReturnType() {
    doTest("Coroutine[Any, Any, int]",
           "async def foo(x):\n" +
           "    await x\n" +
           "    return 0\n" +
           "\n" +
           "def bar(y):\n" +
           "    expr = foo(y)\n");
  }

  public void testAwaitCoroutine() {
    doTest("int",
           "async def foo(x):\n" +
           "    await x\n" +
           "    return 0\n" +
           "\n" +
           "async def bar(y):\n" +
           "    expr = await foo(y)\n");
  }

  // Not in PEP 484 as for now, see https://github.com/ambv/typehinting/issues/119
  public void testCoroutineReturnTypeAnnotation() {
    doTest("int",
           "async def foo() -> int: ...\n" +
           "\n" +
           "async def bar():\n" +
           "    expr = await foo()\n");
  }

  // PY-16987
  public void testNoTypeInGoogleDocstringParamAnnotation() {
    doTest("int", "def f(x: int):\n" +
                  "    \"\"\"\n" +
                  "    Args:\n" +
                  "        x: foo\n" +
                  "    \"\"\"    \n" +
                  "    expr = x");
  }

  // PY-16987
  public void testUnfilledTypeInGoogleDocstringParamAnnotation() {
    doTest("int", "def f(x: int):\n" +
                  "    \"\"\"\n" +
                  "    Args:\n" +
                  "        x (): foo\n" +
                  "    \"\"\"    \n" +
                  "    expr = x");
  }

  // PY-16987
  public void testNoTypeInNumpyDocstringParamAnnotation() {
    doTest("int", "def f(x: int):\n" +
                  "    \"\"\"\n" +
                  "    Parameters\n" +
                  "    ----------\n" +
                  "    x\n" +
                  "        foo\n" +
                  "    \"\"\"\n" +
                  "    expr = x");
  }

  // PY-17010
  public void testAnnotatedReturnTypePrecedesDocstring() {
    doTest("int", "def func() -> int:\n" +
                  "    \"\"\"\n" +
                  "    Returns:\n" +
                  "        str\n" +
                  "    \"\"\"\n" +
                  "expr = func()");
  }

  // PY-17010
  public void testAnnotatedParamTypePrecedesDocstring() {
    doTest("int", "def func(x: int):\n" +
                  "    \"\"\"\n" +
                  "    Args:\n" +
                  "        x (str):\n" +
                  "    \"\"\"\n" +
                  "    expr = x");
  }

  public void testOpenDefault() {
    doTest("TextIO",
           "expr = open('foo')\n");
  }

  public void testOpenText() {
    doTest("TextIO",
           "expr = open('foo', 'r')\n");
  }

  public void testOpenBinary() {
    doTest("BinaryIO",
           "expr = open('foo', 'rb')\n");
  }

  public void testIoOpenDefault() {
    doTest("TextIO",
           "import io\n" +
           "expr = io.open('foo')\n");
  }

  public void testIoOpenText() {
    doTest("TextIO",
           "import io\n" +
           "expr = io.open('foo', 'r')\n");
  }

  public void testIoOpenBinary() {
    doTest("BinaryIO",
           "import io\n" +
           "expr = io.open('foo', 'rb')\n");
  }

  // PY-1427
  public void testBytesLiteral() {
    doTest("bytes", "expr = b'foo'");
  }

  // PY-20770
  public void testAsyncGenerator() {
    doTest("AsyncGenerator[int, Any]",
           "async def asyncgen():\n" +
           "    yield 42\n" +
           "expr = asyncgen()");
  }

  // PY-20770
  public void testAsyncGeneratorDunderAiter() {
    doTest("AsyncGenerator[int, Any]",
           "async def asyncgen():\n" +
           "    yield 42\n" +
           "expr = asyncgen().__aiter__()");
  }

  // PY-20770
  public void testAsyncGeneratorDunderAnext() {
    doTest("Awaitable[int]",
           "async def asyncgen():\n" +
           "    yield 42\n" +
           "expr = asyncgen().__anext__()");
  }

  // PY-20770
  public void testAsyncGeneratorAwaitOnDunderAnext() {
    doTest("int",
           "async def asyncgen():\n" +
           "    yield 42\n" +
           "async def asyncusage()\n" +
           "    expr = await asyncgen().__anext__()");
  }

  // PY-20770
  public void testAsyncGeneratorAsend() {
    doTest("Awaitable[int]",
           "async def asyncgen():\n" +
           "    yield 42\n" +
           "expr = asyncgen().asend(\"hello\")");
  }

  // PY-20770
  public void testAsyncGeneratorAwaitOnAsend() {
    doTest("int",
           "async def asyncgen():\n" +
           "    yield 42\n" +
           "async def asyncusage():\n" +
           "    expr = await asyncgen().asend(\"hello\")");
  }

  // PY-20770
  public void testIteratedAsyncGeneratorElement() {
    doTest("int",
           "async def asyncgen():\n" +
           "    yield 10\n" +
           "async def run():\n" +
           "    async for i in asyncgen():\n" +
           "        expr = i");
  }

  // PY-20770
  public void testElementInAsyncComprehensions() {
    doTest("int",
           "async def asyncgen():\n" +
           "    yield 10\n" +
           "async def run():\n" +
           "    {expr async for expr in asyncgen()}\n");

    doTest("int",
           "async def asyncgen():\n" +
           "    yield 10\n" +
           "async def run():\n" +
           "    [expr async for expr in asyncgen()]\n");

    doTest("int",
           "async def asyncgen():\n" +
           "    yield 10\n" +
           "async def run():\n" +
           "    {expr: expr ** 2 async for expr in asyncgen()}\n");

    doTest("int",
           "async def asyncgen():\n" +
           "    yield 10\n" +
           "async def run():\n" +
           "    (expr async for expr in asyncgen())\n");

    doTest("int",
           "async def asyncgen():\n" +
           "    yield 10\n" +
           "async def run():\n" +
           "    list(expr async for expr in asyncgen())\n");

    doTest("int",
           "async def asyncgen():\n" +
           "    yield 10\n" +
           "async def run():\n" +
           "    dataset = {data async for expr in asyncgen()\n" +
           "                    async for data in asyncgen()\n" +
           "                    if check(data)}\n");

    doTest("int",
           "async def asyncgen():\n" +
           "    yield 10\n" +
           "async def run():\n" +
           "    dataset = {expr async for line in asyncgen()\n" +
           "                    async for expr in asyncgen()\n" +
           "                    if check(expr)}\n");
  }

  // PY-20770
  public void testAwaitInComprehensions() {
    doTest("list[int]",
           "async def asyncgen():\n" +
           "    yield 10\n" +
           "async def run():\n" +
           "    expr = [await z for z in [asyncgen().__anext__()]]\n");
  }

  // PY-20770
  public void testAwaitInAsyncComprehensions() {
    doTest("list[int]",
           "async def asyncgen():\n" +
           "    yield 10\n" +
           "async def asyncgen2():\n" +
           "    yield asyncgen().__anext__()\n" +
           "async def run():\n" +
           "    expr = [await z async for z in asyncgen2()]\n");
  }

  public void testIsNotNone() {
    doTest("int",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if x is not None:\n" +
           "        expr = x\n");

    doTest("int",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if None is not x:\n" +
           "        expr = x\n");

    doTest("int",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if not x is None:\n" +
           "        expr = x\n");

    doTest("int",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if not None is x:\n" +
           "        expr = x\n");
  }

  public void testIsNone() {
    doTest("None",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if x is None:\n" +
           "        expr = x\n");

    doTest("None",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if None is x:\n" +
           "        expr = x\n");
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
           "def get_value(v):\n" +
           "    if v:\n" +
           "        return min(v)\n" +
           "    else:\n" +
           "        return None\n" +
           "expr = get_value([])");
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
    doTest("int | Literal[0]",
           "expr = sum([1, 2, 3])");
  }

  public void testNumpyResolveRaterDoesNotIncreaseRateForNotNdarrayRightOperatorFoundInStub() {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    doTest("D1 | D2",
           "class D1(object):\n" +
           "    pass\n" +
           "class D2(object):\n" +
           "    pass\n" +
           "expr = D1() / D2()");
  }

  // PY-22181
  public void testIterationOverIterableWithSeparateIterator() {
    doTest("int",
           "class AIter(object):\n" +
           "    def __next__(self):\n" +
           "        return 5\n" +
           "class A(object):\n" +
           "    def __iter__(self):\n" +
           "        return AIter()\n" +
           "a = A()\n" +
           "for expr in a:\n" +
           "    print(expr)");
  }

  // PY-22181
  public void testAsyncIterationOverIterableWithSeparateIterator() {
    doTest("int",
           "class AIter(object):\n" +
           "    def __anext__(self):\n" +
           "        return 5\n" +
           "class A(object):\n" +
           "    def __aiter__(self):\n" +
           "        return AIter()\n" +
           "a = A()\n" +
           "async for expr in a:\n" +
           "    print(expr)");
  }

  // PY-21655
  public void testUsageOfFunctionDecoratedWithAsyncioCoroutine() {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    doTest("int",
           "import asyncio\n" +
           "@asyncio.coroutine\n" +
           "def foo():\n" +
           "    yield from asyncio.sleep(1)\n" +
           "    return 3\n" +
           "async def bar():\n" +
           "    expr = await foo()\n" +
           "    return expr");
  }

  // PY-21655
  public void testUsageOfFunctionDecoratedWithTypesCoroutine() {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    doTest("int",
           "import asyncio\n" +
           "import types\n" +
           "@types.coroutine\n" +
           "def foo():\n" +
           "    yield from asyncio.sleep(1)\n" +
           "    return 3\n" +
           "async def bar():\n" +
           "    expr = await foo()\n" +
           "    return expr");
  }

  // PY-22513
  public void testGenericKwargs() {
    doTest("dict[str, int | str]",
           "from typing import Any, Dict, TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "def generic_kwargs(**kwargs: T) -> Dict[str, T]:\n" +
           "    pass\n" +
           "\n" +
           "expr = generic_kwargs(a=1, b='foo')\n");
  }

  // PY-19323
  public void testReturnedTypingCallable() {
    doTest("(...) -> Any",
           "from typing import Callable\n" +
           "def f() -> Callable:\n" +
           "    pass\n" +
           "expr = f()");
  }

  public void testReturnedTypingCallableWithUnknownParameters() {
    doTest("(...) -> int",
           "from typing import Callable\n" +
           "def f() -> Callable[..., int]:\n" +
           "    pass\n" +
           "expr = f()");
  }

  public void testReturnedTypingCallableWithKnownParameters() {
    doTest("(int, str) -> int",
           "from typing import Callable\n" +
           "def f() -> Callable[[int, str], int]:\n" +
           "    pass\n" +
           "expr = f()");
  }

  // PY-24445
  public void testIsSubclassInsideListComprehension() {
    doTest("list[Type[A]]",
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
           "class AContext:\n" +
           "    async def __aenter__(self) -> str:\n" +
           "        pass\n" +
           "async def foo():\n" +
           "    async with AContext() as c:\n" +
           "        expr = c");
  }

  // PY-24067
  public void testAsyncFunctionReturnTypeInDocstring() {
    doTest("Coroutine[Any, Any, int]",
           "async def f():\n" +
           "    \"\"\"\n" +
           "    :rtype: int\n" +
           "    \"\"\"\n" +
           "    pass\n" +
           "expr = f()");
  }

  // PY-27518
  public void testAsyncFunctionReturnTypeInNumpyDocstring() {
    doTest("Coroutine[Any, Any, int]",
           "async def f():\n" +
           "    \"\"\"\n" +
           "    An integer.\n" +
           "\n" +
           "    Returns\n" +
           "    -------\n" +
           "    int\n" +
           "        A number\n" +
           "    \"\"\"\n" +
           "    pass\n" +
           "expr = f()");
  }

  // PY-26847
  public void testAwaitOnImportedCoroutine() {
    doMultiFileTest("Any",
                    "from mycoroutines import mycoroutine\n" +
                    "\n" +
                    "async def main():\n" +
                    "    expr = await mycoroutine()");
  }

  // PY-26643
  public void testReplaceSelfInCoroutine() {
    doTest("Coroutine[Any, Any, B]",
           "class A:\n" +
           "    async def foo(self):\n" +
           "        return self\n" +
           "class B(A):\n" +
           "    pass\n" +
           "expr = B().foo()");
  }

  // PY-4813
  public void testParameterTypeInferenceInSubclassFromDocstring() {
    doTest("int",
           "class Base:\n" +
           "    def test(self, param):\n" +
           "        \"\"\"\n" +
           "        :param param:\n" +
           "        :type param: int\n" +
           "        \"\"\"\n" +
           "        pass\n" +
           "\n" +
           "class Subclass(Base):\n" +
           "    def test(self, param):\n" +
           "        expr = param");
  }

  public void testParameterTypeInferenceInSubclassFromAnnotation() {
    doTest("int",
           "class Base:\n" +
           "    def test(self, param: int) -> int: pass\n" +
           "\n" +
           "class Subclass(Base):\n" +
           "    def test(self, param):\n" +
           "        expr = param");
  }

  public void testParameterTypeInferenceInSubclassHierarchyFromAnnotation1() {
    doTest("int",
           "class Base:\n" +
           "    def test(self, param: int) -> int: pass\n" +
           "\n" +
           "class Base1(Base):\n" +
           "    pass\n" +
           "\n" +
           "class Subclass(Base1):\n" +
           "    def test(self, param):\n" +
           "        expr = param");
  }

  public void testParameterTypeInferenceInSubclassHierarchyFromAnnotation2() {
    doTest("int",
           "class Base1:\n" +
           "    def test(self, param: int) -> int: pass\n" +
           "\n" +
           "class Base2:\n" +
           "    def test(self, param: str) -> str: pass\n" +
           "\n" +
           "class Subclass(Base1, Base2):\n" +
           "    def test(self, param):\n" +
           "        expr = param");
  }

  public void testParameterTypeInferenceInSubclassHierarchyFromAnnotation3() {
    doTest("int",
           "class Base1:\n" +
           "    def test(self, param: int) -> int: pass\n" +
           "\n" +
           "class Base2:\n" +
           "    def test(self, param: str) -> str: pass\n" +
           "\n" +
           "class Base3(Base1):\n" +
           "    pass\n" +
           "\n" +
           "class Subclass(Base3, Base2):\n" +
           "    def test(self, param):\n" +
           "        expr = param");
  }

  public void testParameterTypeInferenceInSubclassHierarchyFromAnnotation4() {
    /*
      This behavior mimics C3 MRO used by *new style* classes for Python 2.3 and below
      For details see: https://www.python.org/download/releases/2.3/mro/
      Since annotations are supported in Python 3.5 and above, *classic classes*
      should not be tested here, but it's NOT the case for docstrings
    */
    doTest("int",
           "class Base1:\n" +
           "    def test(self, param: str) -> str: pass\n" +
           "\n" +
           "class Base2(Base1):\n" +
           "    def test(self, param: int) -> int: pass\n" +
           "\n" +
           "class Base3(Base1):\n" +
           "    pass\n" +
           "\n" +
           "class Subclass(Base3, Base2):\n" +
           "    def test(self, param):\n" +
           "        expr = param");
  }

  public void testParameterTypeInferenceInSubclassHierarchyFromAnnotation5() {
    doTest("int",
           "class Base1:\n" +
           "    def test(self, param: int) -> int: pass\n" +
           "\n" +
           "class Base2(Base1):\n" +
           "    def test(self, param): pass\n" +
           "\n" +
           "class Subclass(Base2):\n" +
           "    def test(self, param):\n" +
           "        expr = param");
  }

  public void testParameterTypeInferenceInOverloadedMethods() {
    doTest("Any",
           "from typing import overload\n" +
           "\n" +
           "class Base:\n" +
           "    @overload\n" +
           "    def test(self, param: int) -> int: pass\n" +
           "\n" +
           "    @overload\n" +
           "    def test(self, param: str) -> str: pass\n" +
           "\n" +
           "class Subclass(Base):\n" +
           "    def test(self, param):\n" +
           "        expr = param");
  }

  public void testParameterTypeInferenceInSubclassHierarchyInStaticMethods() {
    doTest("int",
           "class Base:\n" +
           "    @staticmethod\n" +
           "    def test(param: int, param1: int) -> int: pass\n" +
           "\n" +
           "class Subclass(Base):\n" +
           "    @staticmethod\n" +
           "    def test(param, param1):\n" +
           "        expr = param\n" +
           "\n");
  }

  public void testReturnTypeInferenceInSubclassFromAnnotation() {
    doTest("int",
           "class Base:\n" +
           "    def test(self) -> int: pass\n" +
           "\n" +
           "class Subclass(Base):\n" +
           "    def test(self): pass\n" +
           "\n" +
           "expr = Subclass().test()");
  }

  public void testReturnTypeInferenceInSubclassHierarchyFromAnnotation() {
    doTest("int",
           "class Base:\n" +
           "    def test(self) -> int: pass\n" +
           "\n" +
           "class Base1(Base):\n" +
           "    pass\n" +
           "\n" +
           "class Subclass(Base1):\n" +
           "    def test(self): pass\n" +
           "\n" +
           "expr = Subclass().test()");
  }

  /**
   * TODO: activate when return type information from :rtype: will be available in subclasses.
   *
   * See {@code {@link com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider#getReturnTypeFromSupertype}} javadoc.
   */
  public void ignoreTestReturnTypeInferenceInSubclassFromDocstring() {
    doTest("int",
           "class Base:\n" +
           "    def test(self):" +
           "        \"\"\"\n" +
           "        :rtype: int\n" +
           "        \"\"\"\n" +
           "        pass\n" +
           "\n" +
           "class Subclass(Base):\n" +
           "    def test(self): pass\n" +
           "\n" +
           "expr = Subclass().test()");
  }

  /**
   * TODO: activate when return type information from :rtype: will be available in subclasses.
   *
   * See {@code {@link com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider#getReturnTypeFromSupertype}} javadoc.
   */
  public void ignoreTestReturnTypeInferenceInSubclassHierarchyFromDocstring() {
    doTest("int",
           "class Base:\n" +
           "    def test(self):" +
           "        \"\"\"\n" +
           "        :rtype: int\n" +
           "        \"\"\"\n" +
           "        pass\n" +
           "\n" +
           "class Base1(Base):\n" +
           "    pass\n" +
           "\n" +
           "class Subclass(Base1):\n" +
           "    def test(self): pass\n" +
           "\n" +
           "expr = Subclass().test()");
  }

  // PY-27398
  public void testDataclassPostInitParameter() {
    doTest("int",
           "from dataclasses import dataclass, InitVar\n" +
           "@dataclass\n" +
           "class Foo:\n" +
           "    i: int\n" +
           "    j: int\n" +
           "    d: InitVar[int]\n" +
           "    def __post_init__(self, d):\n" +
           "        expr = d");
  }

  // PY-27398
  public void testDataclassPostInitParameterNoInit() {
    doTest("Any",
           "from dataclasses import dataclass, InitVar\n" +
           "@dataclass(init=False)\n" +
           "class Foo:\n" +
           "    i: int\n" +
           "    j: int\n" +
           "    d: InitVar[int]\n" +
           "    def __post_init__(self, d):\n" +
           "        expr = d");
  }

  // PY-28506
  public void testDataclassPostInitInheritedParameter() {
    // both are dataclasses with enabled `init`
    doTest("int",
           "from dataclasses import dataclass, InitVar\n" +
           "\n" +
           "@dataclass\n" +
           "class A:\n" +
           "    a: InitVar[int]\n" +
           "\n" +
           "@dataclass\n" +
           "class B(A):\n" +
           "    b: InitVar[str]\n" +
           "\n" +
           "    def __post_init__(self, a, b):\n" +
           "        expr = a");
  }

  // PY-28506
  public void testDataclassPostInitInheritedParameter2() {
    // both are dataclasses, base with enabled `init`
    doTest("Any",
           "from dataclasses import dataclass, InitVar\n" +
           "\n" +
           "@dataclass\n" +
           "class A:\n" +
           "    a: InitVar[int]\n" +
           "\n" +
           "@dataclass(init=False)\n" +
           "class B(A):\n" +
           "    b: InitVar[str]\n" +
           "\n" +
           "    def __post_init__(self, a, b):\n" +
           "        expr = a");
  }

  // PY-28506
  public void testDataclassPostInitInheritedParameter3() {
    // both are dataclasses, derived with enabled `init`
    doTest("int",
           "from dataclasses import dataclass, InitVar\n" +
           "\n" +
           "@dataclass(init=False)\n" +
           "class A:\n" +
           "    a: InitVar[int]\n" +
           "\n" +
           "@dataclass\n" +
           "class B(A):\n" +
           "    b: InitVar[str]\n" +
           "\n" +
           "    def __post_init__(self, a, b):\n" +
           "        expr = a");
  }

  // PY-28506
  public void testDataclassPostInitInheritedParameter4() {
    // both are dataclasses with disabled `init`
    doTest("Any",
           "from dataclasses import dataclass, InitVar\n" +
           "\n" +
           "@dataclass(init=False)\n" +
           "class A:\n" +
           "    a: InitVar[int]\n" +
           "\n" +
           "@dataclass(init=False)\n" +
           "class B(A):\n" +
           "    b: InitVar[str]\n" +
           "\n" +
           "    def __post_init__(self, a, b):\n" +
           "        expr = a");
  }

  // PY-28506
  public void testMixedDataclassPostInitInheritedParameter() {
    doTest("Any",
           "from dataclasses import dataclass, InitVar\n" +
           "\n" +
           "class A:\n" +
           "    a: InitVar[int]\n" +
           "\n" +
           "@dataclass\n" +
           "class B(A):\n" +
           "    b: InitVar[str]\n" +
           "\n" +
           "    def __post_init__(self, a, b):\n" +
           "        expr = a");

    doTest("Any",
           "from dataclasses import dataclass, InitVar\n" +
           "\n" +
           "@dataclass\n" +
           "class A:\n" +
           "    a: InitVar[int]\n" +
           "\n" +
           "class B(A):\n" +
           "    b: InitVar[str]\n" +
           "\n" +
           "    def __post_init__(self, a, b):\n" +
           "        expr = a");
  }

  // PY-27783
  public void testApplyingSuperSubstitutionToGenericClass() {
    doTest("dict[T, int]",
           "from typing import TypeVar, Generic, Dict, List\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class A(Generic[T]):\n" +
           "    pass\n" +
           "\n" +
           "class B(A[List[T]], Generic[T]):\n" +
           "    def __init__(self) -> None:\n" +
           "        self.value_set: Dict[T, int] = {}\n" +
           "\n" +
           "    def foo(self) -> None:\n" +
           "        expr = self.value_set");
  }

  // PY-27783
  public void testApplyingSuperSubstitutionToBoundedGenericClass() {
    doTest("dict[T, int]",
           "from typing import TypeVar, Generic, Dict, List\n" +
           "\n" +
           "T = TypeVar('T', bound=str)\n" +
           "\n" +
           "class A(Generic[T]):\n" +
           "    pass\n" +
           "\n" +
           "class B(A[List[T]], Generic[T]):\n" +
           "    def __init__(self) -> None:\n" +
           "        self.value_set: Dict[T, int] = {}\n" +
           "\n" +
           "    def foo(self) -> None:\n" +
           "        expr = self.value_set");
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
           "def example():\n" +
           "    \"\"\"Example Docstring\"\"\"\n" +
           "    return 0\n" +
           "expr = example.__doc__");
  }

  // PY-29891
  public void testContextManagerType() {
    doTest("str",
           "from typing import Type, ContextManager\n" +
           "def example():\n" +
           "  manager: Type[ContextManager[str]]\n" +
           "  with manager() as m:\n" +
           "        expr = m");
  }

  // PY-29891
  public void testAsyncContextManager() {
    doTest("str",
           "from typing import AsyncContextManager\n" +
           "async def example():\n" +
           "    manager: AsyncContextManager[str]\n" +
           "    async with manager as m:\n" +
           "        expr = m");
  }

  // PY-49935
  public void testParamSpecExample() {
    doTest("(str, bool) -> str",
           "from collections.abc import Callable\n" +
           "from typing import ParamSpec\n" +
           "\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "def changes_return_type_to_str(x: Callable[P, int]) -> Callable[P, str]: ...\n" +
           "\n" +
           "\n" +
           "def returns_int(a: str, b: bool) -> int:\n" +
           "    return 42\n" +
           "\n" +
           "\n" +
           "expr = changes_return_type_to_str(returns_int)");
  }

  // PY-49935
  public void testParamSpecSeveral() {
    doTest("(int, str) -> bool",
           "from typing import ParamSpec, Callable\n" +
           "\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "def foo(x: Callable[P, int], y: Callable[P, int]) -> Callable[P, bool]: ...\n" +
           "\n" +
           "\n" +
           "def x_y(x: int, y: str) -> int: ...\n" +
           "\n" +
           "\n" +
           "def y_x(y: int, x: str) -> int: ...\n" +
           "\n" +
           "\n" +
           "expr = foo(x_y, y_x)");
  }

  // PY-49935
  public void testParamSpecUserGenericClass() {
    doTest("Y[int, [int, str, bool]]",
           "from typing import TypeVar, Generic, Callable, ParamSpec\n" +
           "\n" +
           "U = TypeVar(\"U\")\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "class Y(Generic[U, P]):\n" +
           "    f: Callable[P, str]\n" +
           "    attr: U\n" +
           "\n" +
           "    def __init__(self, f: Callable[P, str], attr: U) -> None:\n" +
           "        self.f = f\n" +
           "        self.attr = attr\n" +
           "\n" +
           "\n" +
           "def a(q: int, p: str, r: bool) -> str: ...\n" +
           "\n" +
           "\n" +
           "expr = Y(a, 1)\n");
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethod() {
    doTest("(int) -> str",
           "from typing import TypeVar, Generic, Callable, ParamSpec\n" +
           "\n" +
           "U = TypeVar(\"U\")\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "class Y(Generic[U, P]):\n" +
           "    f: Callable[P, U]\n" +
           "    attr: U\n" +
           "\n" +
           "    def __init__(self, f: Callable[P, U], attr: U) -> None:\n" +
           "        self.f = f\n" +
           "        self.attr = attr\n" +
           "\n" +
           "\n" +
           "def a(q: int) -> str: ...\n" +
           "\n" +
           "\n" +
           "expr = Y(a, '1').f\n");
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethodConcatenate() {
    doTest("(int, str, bool) -> str",
           "from typing import TypeVar, Generic, Callable, ParamSpec, Concatenate\n" +
           "\n" +
           "U = TypeVar(\"U\")\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "class Y(Generic[U, P]):\n" +
           "    f: Callable[Concatenate[int, P], U]\n" +
           "    attr: U\n" +
           "\n" +
           "    def __init__(self, f: Callable[Concatenate[int, P], U], attr: U) -> None:\n" +
           "        self.f = f\n" +
           "        self.attr = attr\n" +
           "\n" +
           "\n" +
           "def a(q: int, s: str, b: bool) -> str: ...\n" +
           "\n" +
           "\n" +
           "expr = Y(a, '1').f\n");
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethodConcatenateSeveralParameters() {
    doTest("(int, bool, str, bool) -> str",
           "from typing import TypeVar, Generic, Callable, ParamSpec, Concatenate\n" +
           "\n" +
           "U = TypeVar(\"U\")\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "class Y(Generic[U, P]):\n" +
           "    f: Callable[Concatenate[int, bool, P], U]\n" +
           "    attr: U\n" +
           "\n" +
           "    def __init__(self, f: Callable[Concatenate[int, bool, P], U], attr: U) -> None:\n" +
           "        self.f = f\n" +
           "        self.attr = attr\n" +
           "\n" +
           "\n" +
           "def a(q: int, r: bool, s: str, b: bool) -> str: ...\n" +
           "\n" +
           "\n" +
           "expr = Y(a, '1').f\n");
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethodConcatenateOtherFunction() {
    doTest("(bool, dict[str, list[str]], str, bool) -> str",
           "from typing import TypeVar, Generic, Callable, ParamSpec, Concatenate\n" +
           "\n" +
           "U = TypeVar(\"U\")\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "class Y(Generic[U, P]):\n" +
           "    f: Callable[Concatenate[int, bool, P], U]\n" +
           "    g: Callable[Concatenate[bool, dict[str, list[str]], P], U]\n" +
           "    attr: U\n" +
           "\n" +
           "    def __init__(self, f: Callable[Concatenate[int, bool, P], U], attr: U) -> None:\n" +
           "        self.f = f\n" +
           "        self.attr = attr\n" +
           "\n" +
           "\n" +
           "def a(q: int, r: bool, s: str, b: bool) -> str: ...\n" +
           "\n" +
           "\n" +
           "expr = Y(a, '1').g\n");
  }

  // PY-49935
  public void testParamSpecUserGenericClassAttribute() {
    doTest("str",
           "from typing import TypeVar, Generic, Callable, ParamSpec\n" +
           "\n" +
           "U = TypeVar(\"U\")\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "class Y(Generic[U, P]):\n" +
           "    f: Callable[P, U]\n" +
           "    attr: U\n" +
           "\n" +
           "    def __init__(self, f: Callable[P, U], attr: U) -> None:\n" +
           "        self.f = f\n" +
           "        self.attr = attr\n" +
           "\n" +
           "\n" +
           "def a(q: int) -> str: ...\n" +
           "\n" +
           "\n" +
           "expr = Y(a, '1').attr\n");
  }

  // PY-49935
  public void testParamSpecConcatenateAdd() {
    doTest("(str, int, tuple[bool, ...]) -> bool",
           "from collections.abc import Callable\n" +
           "from typing import Concatenate, ParamSpec\n" +
           "\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "def bar(x: int, *args: bool) -> int: ...\n" +
           "\n" +
           "\n" +
           "def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...\n" +
           "\n" +
           "\n" +
           "expr = add(bar)  # Should return (__a: str, x: int, *args: bool) -> bool");
  }

  // PY-49935
  public void testParamSpecConcatenateAddSeveralParameters() {
    doTest("(str, bool, int, tuple[bool, ...]) -> bool",
           "from collections.abc import Callable\n" +
           "from typing import Concatenate, ParamSpec\n" +
           "\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "def bar(x: int, *args: bool) -> int: ...\n" +
           "\n" +
           "\n" +
           "def add(x: Callable[P, int]) -> Callable[Concatenate[str, bool, P], bool]: ...\n" +
           "\n" +
           "\n" +
           "expr = add(bar)  # Should return (__a: str, x: int, *args: bool) -> bool");
  }

  // PY-49935
  public void testParamSpecConcatenateRemove() {
    doTest("(tuple[bool, ...]) -> bool",
           "from collections.abc import Callable\n" +
           "from typing import Concatenate, ParamSpec\n" +
           "\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "def bar(x: int, *args: bool) -> int: ...\n" +
           "\n" +
           "\n" +
           "def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...\n" +
           "\n" +
           "\n" +
           "expr = remove(bar)");
  }

  // PY-49935
  public void testParamSpecConcatenateTransform() {
    doTest("(str, tuple[bool, ...]) -> bool",
           "from collections.abc import Callable\n" +
           "from typing import Concatenate, ParamSpec\n" +
           "\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "def bar(x: int, *args: bool) -> int: ...\n" +
           "\n" +
           "\n" +
           "def transform(\n" +
           "        x: Callable[Concatenate[int, P], int]\n" +
           ") -> Callable[Concatenate[str, P], bool]:\n" +
           "    def inner(s: str, *args: P.args):\n" +
           "        return True\n" +
           "    return inner\n" +
           "\n" +
           "\n" +
           "expr = transform(bar)");
  }

  /**
   * @see #testRecursiveDictTopDown()
   * @see PyTypeCheckerInspectionTest#testRecursiveDictAttribute()
   */
  public void testRecursiveDictBottomUp() {
    String text = "class C:\n" +
                  "    def f(self, x):\n" +
                  "        self.foo = x\n" +
                  "        self.foo = {'foo': self.foo}\n" +
                  "        expr = self.foo\n";
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PyExpression dict = myFixture.findElementByText("{'foo': self.foo}", PyExpression.class);
    assertExpressionType("dict[str, Any]", dict);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    assertExpressionType("dict[str, Any]", expr);
  }

  public void testRecursiveDictTopDown() {
    String text = "class C:\n" +
                  "    def f(self, x):\n" +
                  "        self.foo = x\n" +
                  "        self.foo = {'foo': self.foo}\n" +
                  "        expr = self.foo\n";
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    assertExpressionType("dict[str, Any]", expr);
    PyExpression dict = myFixture.findElementByText("{'foo': self.foo}", PyExpression.class);
    assertExpressionType("dict[str, Any]", dict);
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

  private void doMultiFileTest(@NotNull String expectedType, @NotNull String text) {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    doTest(expectedType, text);
  }
}
