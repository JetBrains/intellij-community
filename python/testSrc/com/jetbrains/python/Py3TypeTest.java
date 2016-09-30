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
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doTest("__coroutine[int]",
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
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest("__asyncgenerator[int, Any]",
                                                              "async def asyncgen():\n" +
                                                              "    yield 42\n" +
                                                              "expr = asyncgen()"));
  }

  // PY-20770
  public void testAsyncGeneratorDunderAiter() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest("AsyncIterator[int]",
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
