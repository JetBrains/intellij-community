// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.google.common.collect.ImmutableList;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PyDecoratedFunctionTypeProviderTest extends PyTestCase {

  public void testMakeConstant() {
    doTest("int", "int",
           "def dec(fun):\n" +
           "    return 12\n" +
           "\n" +
           "@dec\n" +
           "def func():\n" +
           "    return 12.1\n" +
           "\n" +
           "value = func\n" +
           "dec_func = func");
  }

  public void testMakeConstantFunction() {
    doTest("int", "() -> int",
           "def dec(fun):\n" +
           "    def wrapper():\n" +
           "        return 12\n" +
           "    return wrapper\n" +
           "\n" +
           "@dec\n" +
           "def func():\n" +
           "    return 12.1\n" +
           "\n" +
           "value = func()\n" +
           "dec_func = func");
  }

  public void testMakeConstantWithArg() {
    doTest("int", "(boo: str) -> int",
           "def dec(fun):\n" +
           "    def wrapper(boo: str):\n" +
           "        return 12\n" +
           "    return wrapper\n" +
           "\n" +
           "@dec\n" +
           "def func():\n" +
           "    return 12.1\n" +
           "\n" +
           "value = func()\n" +
           "dec_func = func");
  }

  public void testMakeKnownWithArg() {
    doTest("str", "(boo: str) -> str",
           "def dec(fun):\n" +
           "    def wrapper(boo: str):\n" +
           "        return str(fun())\n" +
           "    return wrapper\n" +
           "\n" +
           "@dec\n" +
           "def func():\n" +
           "    return 12.1" +
           "\n" +
           "value = func()\n" +
           "dec_func = func");
  }

  public void testMakeDecoratorWithArg() {
    doTest("str", "(boo: str) -> str",
           "def dec(i):\n" +
           "    def dec_(fun):\n" +
           "        def wrapper(boo: str):\n" +
           "            return str(fun())\n" +
           "        return wrapper\n" +
           "    return dec_\n" +
           "\n" +
           "@dec(3)\n" +
           "def func():\n" +
           "    return 12.1\n" +
           "\n" +
           "value = func()\n" +
           "dec_func = func");
  }

  public void testReferenceInside() {
    doTest("str", "(boo: str) -> str",
           "def dec(i):\n" +
           "    def dec_(fun):\n" +
           "        def wrapper(boo: str):\n" +
           "            return str(fun())\n" +
           "        return wrapper\n" +
           "    return dec_\n" +
           "\n" +
           "@dec(3)\n" +
           "def func() -> float:\n" +
           "    value = func()\n" +
           "    dec_func = func\n" +
           "    return 12.1");
  }

  public void testMakeDecoratorWithHint() {
    doTest("str", "(int) -> str",
           "from typing import Callable\n" +
           "\n" +
           "def dec(fun) -> Callable[[int], str]:\n" +
           "    def wrapper():\n" +
           "        return 1\n" +
           "    return wrapper\n" +
           "\n" +
           "@dec\n" +
           "def func(ar: str) -> int:\n" +
           "    return 1\n" +
           "\n" +
           "value = func()\n" +
           "dec_func = func");
  }

  public void testMakeDecoratorWithGenericHint() {
    doTest("str", "(int) -> str",
           "from typing import Callable\n" +
           "from typing import TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "B = TypeVar('B')\n" +
           "def dec(fun: Callable[[B], T]) -> Callable[[T], B]:\n" +
           "    def wrapper():\n" +
           "        return 1\n" +
           "    return wrapper\n" +
           "\n" +
           "@dec\n" +
           "def func(ar: str) -> int:\n" +
           "    return 1\n" +
           "\n" +
           "value = func()\n" +
           "dec_func = func");
  }

  public void testIgnoreWraps() {
    doTest("str", "(boo: str) -> str",
           "from functools import wraps\n" +
           "\n" +
           "def dec(fun):\n" +
           "    @wraps(fun)\n" +
           "    def wrapper(boo: str):\n" +
           "        print(f'{boo} wrapper')\n" +
           "        return str(fun())\n" +
           "    return wrapper\n" +
           "\n" +
           "@dec\n" +
           "def func():\n" +
           "    return 12.1\n" +
           "\n" +
           "value = func()\n" +
           "dec_func = func");
  }

  public void testMakeDecoratorClass() {
    doTest("str", "PZFunc[int, str]",
           "from typing import TypeVar, Generic, Callable\n" +
           "\n" +
           "A = TypeVar('A')\n" +
           "B = TypeVar('B')\n" +
           "\n" +
           "class PZFunc(Generic[A, B]):\n" +
           "    def __init__(self, f: Callable[[A], B]) -> None:\n" +
           "        self._f = f\n" +
           "\n" +
           "    def foo(self, x: A) -> B:\n" +
           "        return self._f(x)\n" +
           "\n" +
           "@PZFunc\n" +
           "def func(n: int) -> str:\n" +
           "    return str(n + 1)\n" +
           "\n" +
           "value = func.foo(5)\n" +
           "dec_func = func");
  }

  public void testMakeDecoratorStack() {
    doTest("int", "(tt: str) -> int",
           "from typing import Callable\n" +
           "from functools import wraps\n" +
           "from typing import TypeVar\n" +
           "\n" +
           "S = TypeVar('S')\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "def dec1(t: T) -> Callable[[Callable[[], S]], Callable[[], T]]:\n" +
           "    pass\n" +
           "\n" +
           "def dec2(t: T):\n" +
           "    def dec(fun: Callable[[], S]):\n" +
           "        @wraps(fun)\n" +
           "        def wrapper(tt: T) -> S:\n" +
           "            return tt + fun()\n" +
           "        return wrapper\n" +
           "    return dec\n" +
           "\n" +
           "@dec2('sd')\n" +
           "@dec1(1)\n" +
           "def func() -> float:\n" +
           "    return 12.1" +
           "\n" +
           "value = func('sd')\n" +
           "dec_func = func");
  }

  public void testDecoratorWithArg() {
    doTest("float", "(int) -> float",
           "from typing import Callable\n" +
           "from typing import TypeVar\n" +
           "\n" +
           "I = TypeVar('I')\n" +
           "T = TypeVar('T')\n" +
           "W = TypeVar('W')\n" +
           "\n" +
           "def dec(t: T) -> Callable[ [Callable[[I], W]], Callable[[I], T] ]:\n" +
           "    def dec_(fun):\n" +
           "        def wrapper():\n" +
           "            return t\n" +
           "        return wrapper\n" +
           "    return dec_\n" +
           "\n" +
           "@dec(12.1)\n" +
           "def func(i: int) -> int:\n" +
           "    return i\n" +
           "\n" +
           "value = func(1)\n" +
           "dec_func = func");
  }

  public void testDecorateClass() {
    doTest("str", "(int) -> str",
           "from typing import Callable\n" +
           "\n" +
           "def dec(fun) -> Callable[[int], str]:\n" +
           "    def decor(i):\n" +
           "        return 'sd'\n" +
           "    return decor\n" +
           "\n" +
           "@dec\n" +
           "class Func:\n" +
           "    x: int\n" +
           "\n" +
           "value = Func(1)\n" +
           "dec_func = Func");
  }

  public void testImportDecoratedFunctionType() {
    doMultiFileTest("int", "(str) -> int");
  }

  public void testImportDecoratedWithArgFunctionType() {
    doMultiFileTest("str", "(int) -> str");
  }

  public void testImportDecoratedWithGenericArgFunctionType() {
    initMultiFileTest();
    checkMultiFileTest("str", "(Any) -> str", Context.ANALYSIS);
    checkMultiFileTest("str", "(int) -> str", Context.USER_INITIATED);
  }

  private void doTest(@NotNull String expectedValueType, @NotNull String expectedFuncType, @NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    checkTypes(expectedValueType, expectedFuncType, allContexts());
  }

  private void doMultiFileTest(@NotNull String expectedValueType, @NotNull String expectedFuncType) {
    initMultiFileTest();
    checkMultiFileTest(expectedValueType, expectedFuncType, Context.BOTH);
  }

  private void initMultiFileTest() {
    myFixture.copyDirectoryToProject("/types/" + getTestName(false), "");
    myFixture.configureFromTempProjectFile("a.py");
  }

  private void checkMultiFileTest(@NotNull String expectedValueType, @NotNull String expectedFuncType, @NotNull Context context) {
    List<TypeEvalContext> contexts = context == Context.BOTH
                                     ? allContexts()
                                     : context == Context.ANALYSIS
                                       ? analysisContext()
                                       : userInitiatedContext();
    checkTypes(expectedValueType, expectedFuncType, contexts);
  }


  private void checkTypes(@NotNull String expectedValueType, @NotNull String expectedFuncType, @NotNull List<TypeEvalContext> contexts) {
    for (TypeEvalContext context : contexts) {
      assertType(expectedValueType, myFixture.findElementByText("value", PyExpression.class), context);
      assertType(expectedFuncType, myFixture.findElementByText("dec_func", PyExpression.class), context);
      assertProjectFilesNotParsed(context);
    }
  }

  private List<TypeEvalContext> allContexts() {
    return ImmutableList.of(
      TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile()).withTracing(),
      TypeEvalContext.userInitiated(myFixture.getProject(), myFixture.getFile()).withTracing()
    );
  }

  private List<TypeEvalContext> analysisContext() {
    return ImmutableList.of(
      TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile()).withTracing()
    );
  }

  private List<TypeEvalContext> userInitiatedContext() {
    return ImmutableList.of(
      TypeEvalContext.userInitiated(myFixture.getProject(), myFixture.getFile()).withTracing()
    );
  }

  private enum Context {
    USER_INITIATED,
    ANALYSIS,
    BOTH
  }
}
