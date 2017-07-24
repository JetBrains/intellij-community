/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author vlan
 */
public class Py3TypeTest extends PyTestCase {
  public static final String TEST_DIRECTORY = "/types/";

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  // PY-6702
  public void testYieldFromType() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, () -> doTest("Union[str, int, float]",
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
           "    pass\n"));
  }

  // PY-12944
  public void testYieldFromReturnType() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, () -> doTest("None",
           "def a():\n" +
           "    yield 1\n" +
           "    return 'a'\n" +
           "\n" +
           "y = [1, 2, 3]\n" +
           "\n" +
           "def b():\n" +
           "    expr = yield from y\n" +
           "    return expr\n"));
    runWithLanguageLevel(LanguageLevel.PYTHON33, () -> doTest("str",
           "def a():\n" +
           "    yield 1\n" +
           "    return 'a'\n" +
           "\n" +
           "def b():\n" +
           "    expr = yield from a()\n" +
           "    return expr\n"));
    runWithLanguageLevel(LanguageLevel.PYTHON33, () -> doTest("int",
           "def g():\n" +
           "    yield 1\n" +
           "    return 'abc'\n" +
           "\n" +
           "def f()\n" +
           "    x = yield from g()\n" +
           "\n" +
           "for expr in f():\n" +
           "    pass"));
  }

  public void testYieldFromHomogeneousTuple() {
    myFixture.copyDirectoryToProject("typing", "");
    doTest("str",
           "import typing\n"+
           "def get_tuple() -> typing.Tuple[str, ...]:\n" +
           "    pass\n" +
           "def gen()\n" +
           "    yield from get_tuple()\n" +
           "for expr in gen():" +
           "    pass");
  }

  public void testYieldFromHeterogeneousTuple() {
    myFixture.copyDirectoryToProject("typing", "");
    doTest("Union[int, str]",
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
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doTest("int",
           "class C:\n" +
           "    def __await__(self):\n" +
           "        yield 'foo'\n" +
           "        return 0\n" +
           "\n" +
           "async def foo():\n" +
           "    c = C()\n" +
           "    expr = await c\n"));
  }

  public void testAsyncDefReturnType() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doTest("Coroutine[Any, Any, int]",
           "async def foo(x):\n" +
           "    await x\n" +
           "    return 0\n" +
           "\n" +
           "def bar(y):\n" +
           "    expr = foo(y)\n"));
  }

  public void testAwaitCoroutine() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doTest("int",
           "async def foo(x):\n" +
           "    await x\n" +
           "    return 0\n" +
           "\n" +
           "async def bar(y):\n" +
           "    expr = await foo(y)\n"));
  }

  // Not in PEP 484 as for now, see https://github.com/ambv/typehinting/issues/119
  public void testCoroutineReturnTypeAnnotation() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doTest("int",
           "async def foo() -> int: ...\n" +
           "\n" +
           "async def bar():\n" +
           "    expr = await foo()\n"));
  }
  
  // PY-16987
  public void testNoTypeInGoogleDocstringParamAnnotation() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, () -> doTest("int", "def f(x: int):\n" +
                                                                 "    \"\"\"\n" +
                                                                 "    Args:\n" +
                                                                 "        x: foo\n" +
                                                                 "    \"\"\"    \n" +
                                                                 "    expr = x"));
  }
  
  // PY-16987
  public void testUnfilledTypeInGoogleDocstringParamAnnotation() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, () -> doTest("int", "def f(x: int):\n" +
                                                                 "    \"\"\"\n" +
                                                                 "    Args:\n" +
                                                                 "        x (): foo\n" +
                                                                 "    \"\"\"    \n" +
                                                                 "    expr = x"));
  }
  
  // PY-16987
  public void testNoTypeInNumpyDocstringParamAnnotation() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, () -> doTest("int", "def f(x: int):\n" +
                                                                 "    \"\"\"\n" +
                                                                 "    Parameters\n" +
                                                                 "    ----------\n" +
                                                                 "    x\n" +
                                                                 "        foo\n" +
                                                                 "    \"\"\"\n" +
                                                                 "    expr = x"));
  }
  
  // PY-17010
  public void testAnnotatedReturnTypePrecedesDocstring() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, () -> doTest("int", "def func() -> int:\n" +
                                                                 "    \"\"\"\n" +
                                                                 "    Returns:\n" +
                                                                 "        str\n" +
                                                                 "    \"\"\"\n" +
                                                                 "expr = func()"));
  }

  // PY-17010
  public void testAnnotatedParamTypePrecedesDocstring() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, () -> doTest("int", "def func(x: int):\n" +
                                                                 "    \"\"\"\n" +
                                                                 "    Args:\n" +
                                                                 "        x (str):\n" +
                                                                 "    \"\"\"\n" +
                                                                 "    expr = x"));
  }

  public void testOpenDefault() {
    doTest("TextIOWrapper[str]",
           "expr = open('foo')\n");
  }

  public void testOpenText() {
    doTest("TextIOWrapper[str]",
           "expr = open('foo', 'r')\n");
  }

  public void testOpenBinary() {
    doTest("FileIO[bytes]",
           "expr = open('foo', 'rb')\n");
  }

  // PY-1427
  public void testBytesLiteral() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, () -> doTest("bytes", "expr = b'foo'"));
  }

  // PY-20770
  public void testAsyncGenerator() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest("AsyncGenerator[int, Any]",
                                                              "async def asyncgen():\n" +
                                                              "    yield 42\n" +
                                                              "expr = asyncgen()"));
  }

  // PY-20770
  public void testAsyncGeneratorDunderAiter() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest("AsyncGenerator[int, Any]",
                                                              "async def asyncgen():\n" +
                                                              "    yield 42\n" +
                                                              "expr = asyncgen().__aiter__()"));
  }

  // PY-20770
  public void testAsyncGeneratorDunderAnext() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest("Awaitable[int]",
                                                              "async def asyncgen():\n" +
                                                              "    yield 42\n" +
                                                              "expr = asyncgen().__anext__()"));
  }

  // PY-20770
  public void testAsyncGeneratorAwaitOnDunderAnext() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest("int",
                                                              "async def asyncgen():\n" +
                                                              "    yield 42\n" +
                                                              "async def asyncusage()\n" +
                                                              "    expr = await asyncgen().__anext__()"));
  }

  // PY-20770
  public void testAsyncGeneratorAsend() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest("Awaitable[int]",
                                                              "async def asyncgen():\n" +
                                                              "    yield 42\n" +
                                                              "expr = asyncgen().asend(\"hello\")"));
  }

  // PY-20770
  public void testAsyncGeneratorAwaitOnAsend() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest("int",
                                                              "async def asyncgen():\n" +
                                                              "    yield 42\n" +
                                                              "async def asyncusage():\n" +
                                                              "    expr = await asyncgen().asend(\"hello\")"));
  }

  // PY-20770
  public void testIteratedAsyncGeneratorElement() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest("int",
                                                              "async def asyncgen():\n" +
                                                              "    yield 10\n" +
                                                              "async def run():\n" +
                                                              "    async for i in asyncgen():\n" +
                                                              "        expr = i"));
  }

  // PY-20770
  public void testElementInAsyncComprehensions() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
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
    );
  }

  // PY-20770
  public void testAwaitInComprehensions() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest("List[int]",
                                                              "async def asyncgen():\n" +
                                                              "    yield 10\n" +
                                                              "async def run():\n" +
                                                              "    expr = [await z for z in [asyncgen().__anext__()]]\n"));
  }

  // PY-20770
  public void testAwaitInAsyncComprehensions() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest("List[int]",
                                                              "async def asyncgen():\n" +
                                                              "    yield 10\n" +
                                                              "async def asyncgen2():\n" +
                                                              "    yield asyncgen().__anext__()\n" +
                                                              "async def run():\n" +
                                                              "    expr = [await z async for z in asyncgen2()]\n"));
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
    doTest("Optional[Any]",
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
    doTest("int",
           "expr = sum([1, 2, 3])");
  }

  public void testNumpyResolveRaterDoesNotIncreaseRateForNotNdarrayRightOperatorFoundInStub() {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    doTest("Union[D1, D2]",
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
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doTest("int",
                                                              "import asyncio\n" +
                                                              "@asyncio.coroutine\n" +
                                                              "def foo():\n" +
                                                              "    yield from asyncio.sleep(1)\n" +
                                                              "    return 3\n" +
                                                              "async def bar():\n" +
                                                              "    expr = await foo()\n" +
                                                              "    return expr"));
  }

  // PY-21655
  public void testUsageOfFunctionDecoratedWithTypesCoroutine() {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doTest("int",
                                                              "import asyncio\n" +
                                                              "import types\n" +
                                                              "@types.coroutine\n" +
                                                              "def foo():\n" +
                                                              "    yield from asyncio.sleep(1)\n" +
                                                              "    return 3\n" +
                                                              "async def bar():\n" +
                                                              "    expr = await foo()\n" +
                                                              "    return expr"));
  }

  // PY-22513
  public void testGenericKwargs() {
    doTest("Dict[str, Union[int, str]]",
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
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("(...) -> Any",
                   "from typing import Callable\n" +
                   "def f() -> Callable:\n" +
                   "    pass\n" +
                   "expr = f()")
    );
  }

  public void testReturnedTypingCallableWithUnknownParameters() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("(...) -> int",
                   "from typing import Callable\n" +
                   "def f() -> Callable[..., int]:\n" +
                   "    pass\n" +
                   "expr = f()")
    );
  }

  public void testReturnedTypingCallableWithKnownParameters() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("(int, str) -> int",
                   "from typing import Callable\n" +
                   "def f() -> Callable[[int, str], int]:\n" +
                   "    pass\n" +
                   "expr = f()")
    );
  }

  // PY-24445
  public void testIsSubclassInsideListComprehension() {
    doTest("List[Type[A]]",
           "class A: pass\n" +
           "expr = [e for e in [] if issubclass(e, A)]");
  }

  public void testIsInstanceInsideListComprehension() {
    doTest("List[A]",
           "class A: pass\n" +
           "expr = [e for e in [] if isinstance(e, A)]");
  }

  private void doTest(final String expectedType, final String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    final Project project = expr.getProject();
    final PsiFile containingFile = expr.getContainingFile();
    assertType(expectedType, expr, TypeEvalContext.codeAnalysis(project, containingFile));
    assertType(expectedType, expr, TypeEvalContext.userInitiated(project, containingFile));
  }

  private static void assertType(String expectedType, PyExpression expr, TypeEvalContext context) {
    final PyType actual = context.getType(expr);
    final String actualType = PythonDocumentationProvider.getTypeName(actual, context);
    assertEquals(expectedType, actualType);
  }
}
