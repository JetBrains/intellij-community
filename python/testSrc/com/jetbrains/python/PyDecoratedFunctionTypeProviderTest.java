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
           """
             def dec(fun) -> int:
                 return 12

             @dec
             def func():
                 return 12.1

             value = func
             dec_func = func""");
  }

  public void testMakeConstantFunction() {
    doTest("int", "() -> int",
           """
             from typing import Callable
             
             def dec(fun) -> Callable[[], int]:
                 def wrapper():
                     return 12
                 return wrapper

             @dec
             def func():
                 return 12.1

             value = func()
             dec_func = func""");
  }

  public void testMakeConstantWithArg() {
    doTest("int", "(str) -> int",
           """
             from typing import Callable
             
             def dec(fun) -> Callable[[str], int]:
                 def wrapper(boo: str):
                     return 12
                 return wrapper

             @dec
             def func():
                 return 12.1

             value = func()
             dec_func = func""");
  }

  public void testMakeKnownWithArg() {
    doTest("str", "(str) -> str",
           """
             from typing import Callable
             
             def dec(fun) -> Callable[[str], str]:
                 def wrapper(boo: str):
                     return str(fun())
                 return wrapper

             @dec
             def func():
                 return 12.1
             value = func()
             dec_func = func""");
  }

  public void testMakeDecoratorWithArg() {
    doTest("str", "(str) -> str",
           """
             from typing import Callable
             
             def dec(i) -> Callable[[Callable[[], float]], Callable[[str], str]]:
                 def dec_(fun):
                     def wrapper(boo: str):
                         return str(fun())
                     return wrapper
                 return dec_

             @dec(3)
             def func():
                 return 12.1

             value = func()
             dec_func = func""");
  }

  public void testReferenceInside() {
    doTest("str", "(str) -> str",
           """
             from typing import Callable
             
             def dec(i) -> Callable[[Callable[[], float]], Callable[[str], str]]:
                 def dec_(fun):
                     def wrapper(boo: str):
                         return str(fun())
                     return wrapper
                 return dec_

             @dec(3)
             def func() -> float:
                 value = func()
                 dec_func = func
                 return 12.1""");
  }

  public void testMakeDecoratorWithHint() {
    doTest("str", "(int) -> str",
           """
             from typing import Callable

             def dec(fun) -> Callable[[int], str]:
                 def wrapper():
                     return 1
                 return wrapper

             @dec
             def func(ar: str) -> int:
                 return 1

             value = func()
             dec_func = func""");
  }

  public void testMakeDecoratorWithGenericHint() {
    doTest("str", "(int) -> str",
           """
             from typing import Callable
             from typing import TypeVar

             T = TypeVar('T')
             B = TypeVar('B')
             def dec(fun: Callable[[B], T]) -> Callable[[T], B]:
                 def wrapper():
                     return 1
                 return wrapper

             @dec
             def func(ar: str) -> int:
                 return 1

             value = func()
             dec_func = func""");
  }

  public void testIgnoreWraps() {
    doTest("str", "(str) -> str",
           """
             from functools import wraps
             from typing import Callable

             def dec(fun) -> Callable[[str], str]:
                 @wraps(fun)
                 def wrapper(boo: str):
                     print(f'{boo} wrapper')
                     return str(fun())
                 return wrapper

             @dec
             def func():
                 return 12.1

             value = func()
             dec_func = func""");
  }

  public void testMakeDecoratorClass() {
    doTest("str", "PZFunc[int, str]",
           """
             from typing import TypeVar, Generic, Callable

             A = TypeVar('A')
             B = TypeVar('B')

             class PZFunc(Generic[A, B]):
                 def __init__(self, f: Callable[[A], B]) -> None:
                     self._f = f

                 def foo(self, x: A) -> B:
                     return self._f(x)

             @PZFunc
             def func(n: int) -> str:
                 return str(n + 1)

             value = func.foo(5)
             dec_func = func""");
  }

  public void testMakeDecoratorStack() {
    doTest("int", "(str) -> int",
           """
             from typing import Callable
             from functools import wraps
             from typing import TypeVar

             S = TypeVar('S')
             T = TypeVar('T')

             def dec1(t: T) -> Callable[[Callable[[], S]], Callable[[], T]]:
                 pass

             def dec2(t: T) -> Callable[[Callable[[], S]], Callable[[T], S]]:
                 def dec(fun: Callable[[], S]):
                     @wraps(fun)
                     def wrapper(tt: T) -> S:
                         return tt + fun()
                     return wrapper
                 return dec

             @dec2('sd')
             @dec1(1)
             def func() -> float:
                 return 12.1
             value = func('sd')
             dec_func = func""");
  }

  public void testDecoratorWithArg() {
    doTest("float", "(int) -> float",
           """
             from typing import Callable
             from typing import TypeVar

             I = TypeVar('I')
             T = TypeVar('T')
             W = TypeVar('W')

             def dec(t: T) -> Callable[ [Callable[[I], W]], Callable[[I], T] ]:
                 def dec_(fun):
                     def wrapper():
                         return t
                     return wrapper
                 return dec_

             @dec(12.1)
             def func(i: int) -> int:
                 return i

             value = func(1)
             dec_func = func""");
  }

  public void testDecorateClass() {
    doTest("str", "(int) -> str",
           """
             from typing import Callable

             def dec(fun) -> Callable[[int], str]:
                 def decor(i):
                     return 'sd'
                 return decor

             @dec
             class Func:
                 x: int

             value = Func(1)
             dec_func = Func""");
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

  // PY-48338
  public void testImportDecoratorNotChangeSignatureWithAnnotation() {
    doMultiFileTest("int", "(str) -> int");
  }

  // PY-48338
  public void testImportDecoratorNotChangeSignatureNoAnnotation() {
    doMultiFileTest("int", "(name_in_func: str) -> int");
  }

  // PY-48338
  public void testImportDecoratorChangeSignatureNoAnnotation() {
    doMultiFileTest("int", "(i: str) -> int");
  }

  // PY-48338
  public void testImportDecoratorChangeSignatureWithAnnotation() {
    doMultiFileTest("str", "(int, bool) -> str");
  }

  // PY-48338
  public void testImportTwoDecoratorsFirstChangeSignatureNoAnnotation() {
    doMultiFileTest("int", "(i: str) -> int");
  }

  // PY-48338
  public void testImportTwoDecoratorsFirstChangeSignatureWithAnnotation() {
    doMultiFileTest("str", "(int, bool) -> str");
  }

  // PY-48338
  public void testImportTwoDecoratorsNotChangeSignatureNoAnnotation() {
    doMultiFileTest("int", "(i: str) -> int");
  }

  // PY-48338
  public void testImportTwoDecoratorsNotChangeSignatureWithAnnotation() {
    doMultiFileTest("int", "(str) -> int");
  }

  // PY-48338
  public void testImportTwoDecoratorsSecondChangeSignatureNoAnnotation() {
    doMultiFileTest("int", "(i: str) -> int");
  }

  // PY-48338
  public void testImportTwoDecoratorsSecondChangeSignatureWithAnnotation() {
    doMultiFileTest("str", "(int, bool) -> str");
  }

  // PY-48338
  public void testImportTwoDecoratorsFirstChangeSignatureWithAnnotationParamSpec() {
    doMultiFileTest("list[int]", "(int, i: str) -> list[int]");
  }

  // PY-48338
  public void testImportTwoDecoratorsSecondChangeSignatureWithAnnotationParamSpec() {
    doMultiFileTest("list[int]", "(int, i: str) -> list[int]");
  }

  // PY-49935
  public void testParamSpec() {
    doTest("int", "(x: int, y: str) -> int",
           """
             from typing import Callable, ParamSpec, TypeVar

             P = ParamSpec("P")
             R = TypeVar("R")


             def log_to_database():
                 print('42')


             def add_logging(f: Callable[P, R]) -> Callable[P, R]:
                 def inner(*args: P.args, **kwargs: P.kwargs) -> R:
                     log_to_database()
                     return f(*args, **kwargs)

                 return inner


             @add_logging
             def takes_int_str(x: int, y: str) -> int:
                 return x + len(y)



             value = takes_int_str(1, "A")
             dec_func = takes_int_str
             """);
  }

  // PY-49935
  public void testParamSpecAndConcatenate() {
    doTest("int", "(x: int, y: str) -> int",
           """
             from typing import Concatenate, Callable, ParamSpec, TypeVar

             P = ParamSpec("P")
             R = TypeVar("R")


             class Request:
                 def foo(self):
                     pass


             def with_request(f: Callable[Concatenate[Request, P], R]) -> Callable[P, R]:
                 def inner(*args: P.args, **kwargs: P.kwargs) -> R:
                     return f(Request(), *args, **kwargs)

                 return inner


             @with_request
             def takes_int_str(request: Request, x: int, y: str) -> int:
                 request.foo()
                 return x + len(y)


             value = takes_int_str(1, "A")
             dec_func = takes_int_str
             """);
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
