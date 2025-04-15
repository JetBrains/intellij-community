// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class Py3ArgumentListInspectionTest extends PyInspectionTestCase {
  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyArgumentListInspection.class;
  }

  // PY-36158
  public void testDataclassesStarImportNoUnexpectedArgumentWarning() {
    doTestByText("""
                   from dataclasses import *


                   @dataclass(eq=True)
                   class Foo:
                       a: float
                       b: float


                   print(Foo(1, 2))
                   """);
  }

  // PY-59198
  public void testAttrFieldAliasParameter() {
    runWithAdditionalClassEntryInSdkRoots("packages", () -> {
      doMultiFileTest();
    });
  }

  // PY-54560
  public void testDataclassTransformFieldAliasParameter() {
    doMultiFileTest();
  }

  // PY-50404
  public void testPassingKeywordArgumentsToParamSpec() {
    doTestByText("""
                   from typing import Callable,  ParamSpec

                   P = ParamSpec("P")


                   def changes_return_type_to_str(x: Callable[P, int]) -> Callable[P, str]:
                       def inner(*args: P.args, **kwargs: P.kwargs) -> str:
                           return "42"
                       return inner


                   def returns_int(a: str, b: bool) -> int:
                       return 42


                   f = changes_return_type_to_str(returns_int)
                   res2 = f(a="A", b=True)""");
  }

  // PY-53611
  public void testTypedDictWithRequiredAndNotRequiredKeys() {
    doTestByText("""
                   from typing_extensions import TypedDict, Required, NotRequired
                   class A(TypedDict):
                       x: int
                       y: NotRequired[int]
                   class B(TypedDict, total=False):
                       x: Required[int]
                       y: int
                   a = A(<warning descr="Parameter 'x' unfilled">)</warning>
                   b = B(<warning descr="Parameter 'x' unfilled">)</warning>""");
  }

  // PY-53671
  public void testBoundMethodExportedAsTopLevelFunctionImportedWithQualifiedImport() {
    doMultiFileTest();
  }

  // PY-53671
  public void testBoundMethodExportedAsTopLevelFunctionImportedWithFromImport() {
    doMultiFileTest();
  }

  // PY-53671
  public void testStaticMethodExportedAsTopLevelFunctionImportedWithQualifiedImport() {
    doMultiFileTest();
  }

  // PY-53671
  public void testRandomRandint() {
    doTestByText("""
                   import random

                   random.randint(1, 2)""");
  }

  // PY-53388
  public void testEnumAuto() {
    doTestByText("""
                   import enum

                   class MyEnum(enum.Enum):
                       FOO = enum.auto()
                       BAR = enum.auto()
                   """);
  }

  // PY-27398
  public void testInitializingDataclass() {
    doMultiFileTest();
  }

  // PY-28957
  public void testDataclassesReplace() {
    doMultiFileTest();
  }

  // PY-59760
  public void testNoWarningStarArgumentParamSpec() {
    doTestByText("""
                   import logging
                   from typing import Callable, TypeVar
                   from typing import ParamSpec
                                     
                   P = ParamSpec('P')
                   R = TypeVar('R')
                                     
                                     
                   def outer_decorator(f: Callable[P, R]) -> Callable[P, R]:
                       def inner(*args: P.args, **kwargs: P.kwargs) -> R:
                           logging.info(f'{f.__name__} was called')
                           return f(*args, **kwargs)
                                     
                       return inner
                                     
                                     
                   @outer_decorator
                   def non_working_function(x: float, y: float) -> float:
                       return x + y
                                     
                                     
                   non_working_function(1.1, 2.2)
                   """);
  }

  // PY-70484
  public void testParamSpecInDecoratorReturnTypeCannotBeBoundFromArguments() {
    doTestByText("""
                   from typing import Callable, Any, ParamSpec
                                      
                   P = ParamSpec("P")
                                      
                   def deco(fn: Callable[..., Any]) -> Callable[P, Any]:
                       return fn
                                      
                   @deco
                   def f(x: int):
                       pass
                                      
                   f("foo", 42)
                   """);
  }

  // PY-70484
  public void testParamSpecInDecoratorReturnTypeUnboundDueToUnresolvedArgument() {
    doTestByText("""
                   from typing import Callable, Any, ParamSpec
                                      
                   P = ParamSpec("P")
                                      
                   def deco(fn: Callable[P, Any]) -> Callable[P, Any]:
                       return fn
                                      
                   deco(unresolved)("foo", 42)
                   """);
  }

  // PY-65385
  public void testImportedFunctionDecoratedWithAsyncContextManager() {
    doMultiFileTest();
  }

  // PY-55044
  public void testTypedDictKwargsArgumentWithNonexistentKey() {
    doTestByText("""
                   from typing import TypedDict, Unpack
                                      
                   class Movie(TypedDict):
                       pass

                   def foo(**x: Unpack[Movie]):
                       pass
                       
                   foo(<warning descr="Unexpected argument">nonexistent_key=1</warning>)
                   """);
  }

  // PY-55044
  public void testTypedDictWithRequiredKeyKwargsArgument() {
    doTestByText("""
                   from typing import Required, TypedDict, Unpack
                                      
                   class Movie(TypedDict, total=False):
                       title: Required[str]
                       year: int

                   def foo(**x: Unpack[Movie]):
                       pass
                       
                   foo(<warning descr="Parameter 'title' unfilled">)</warning>
                   foo(year=1982<warning descr="Parameter 'title' unfilled">)</warning>
                   foo(title='Blade Runner')
                   foo(title='Blade Runner', year=1982)
                   """);
  }

  // PY-55044
  public void testTypedDictWithNotRequiredKeyKwargsArgument() {
    doTestByText("""
                   from typing import NotRequired, TypedDict, Unpack
                                      
                   class Movie(TypedDict):
                       title: str
                       year: NotRequired[int]

                   def foo(**x: Unpack[Movie]):
                       pass
                       
                   foo(<warning descr="Parameter 'title' unfilled">)</warning>
                   foo(year=1982<warning descr="Parameter 'title' unfilled">)</warning>
                   foo(title='Blade Runner')
                   foo(title='Blade Runner', year=1982)
                   """);
  }

  // PY-53693
  public void testInitializingDataclassWithKwOnlyAttribute() {
    doTestByText("""
                   from dataclasses import dataclass, KW_ONLY

                   @dataclass
                   class MyClass:
                       a: int
                       qq: KW_ONLY
                       b: int

                   MyClass(0, b=0)
                   MyClass(0, <warning descr="Unexpected argument">0</warning><warning descr="Parameter 'b' unfilled">)</warning>
                   """);
  }

  // PY-53693
  public void testInitializingDerivedDataclassWithKwOnlyAttribute() {
    doTestByText("""
                   from dataclasses import dataclass, KW_ONLY

                   @dataclass
                   class Base:
                       a: int
                       qq: KW_ONLY
                       b: int

                   @dataclass
                   class Derived(Base):
                       c: int
                       ww: KW_ONLY
                       d: int

                   Derived(0, 0, b=0, d=0)
                   Derived(0, 0, <warning descr="Unexpected argument">0</warning>, b=0<warning descr="Parameter 'd' unfilled">)</warning>
                   Derived(0, 0, <warning descr="Unexpected argument">0</warning>, d=0<warning descr="Parameter 'b' unfilled">)</warning>
                   """);
  }

  // PY-53693
  public void testInitializingDerivedDataclassWithOverridenAttribute() {
    doTestByText("""
                   from dataclasses import dataclass, KW_ONLY
                                      
                   @dataclass
                   class Base:
                       a: int
                       qq: KW_ONLY
                       b: int
                                      
                   @dataclass
                   class Derived(Base):
                       b: int

                   Derived(0, 0)
                   """);
  }

  // PY-53693
  public void testInitializingDerivedDataclassWithOverridenKwOnlyAttribute() {
    doTestByText("""
                   from dataclasses import dataclass, KW_ONLY

                   @dataclass
                   class Base:
                       a: int
                       qq: KW_ONLY
                       b: int

                   @dataclass
                   class Derived1(Base):
                       qq: int

                   Derived1(0, 0, b=0)
                   Derived1(0, 0, <warning descr="Unexpected argument">0</warning><warning descr="Parameter 'b' unfilled">)</warning>
                   
                   @dataclass
                   class Derived2(Base):
                       ww: KW_ONLY
                       qq: int

                   Derived2(0, b=0, qq=0)
                   Derived2(0, <warning descr="Unexpected argument">0</warning>, qq=0<warning descr="Parameter 'b' unfilled">)</warning>
                   Derived2(0, <warning descr="Unexpected argument">0</warning>, b=0<warning descr="Parameter 'qq' unfilled">)</warning>
                   """);
  }

  // PY-23067
  public void testFunctoolsWraps() {
    doTestByText("""
                   import functools
                                      
                   class MyClass:
                     def foo(self, s: str, i: int):
                         pass
                                      
                   class Route:
                       @functools.wraps(MyClass.foo)
                       def __init__(self):
                           pass
                                      
                   class Router:
                       @functools.wraps(wrapped=Route.__init__)
                       def route(self, s: str):
                           pass
                                      
                   r = Router()
                   r.route("", 13)
                   r.route(""<warning descr="Parameter 'i' unfilled">)</warning>
                   r.route("", 13, <warning descr="Unexpected argument">1</warning>)
                   """);
  }

  // PY-23067
  public void testFunctoolsWrapsMultiFile() {
    doMultiFileTest();
  }

  public void testInitByDataclassTransformOnDecorator() {
    doMultiFileTest();
  }

  public void testInitByDataclassTransformOnBaseClass() {
    doMultiFileTest();
  }

  public void testInitByDataclassTransformOnMetaClass() {
    doMultiFileTest();
  }

  // PY-42137
  public void testMismatchedOverloadsHaveBothTooFewAndTooManyParameters() {
    doTest();
  }

  // PY-42137
  public void testMismatchedConditionalImplementationsHaveBothTooFewAndTooManyParameters() {
    doTest();
  }

  public void testNoTypeCheck() {
    doTestByText(
      """
        from typing import no_type_check
        
        @no_type_check
        def func(a): ...
        
        func(<warning descr="Parameter 'a' unfilled">)</warning>
        func(1, <warning descr="Unexpected argument">2</warning>)
        """
    );
    doTestByText(
      """
        from typing_extensions import no_type_check
        
        @no_type_check
        def func(a): ...
        
        func(<warning descr="Parameter 'a' unfilled">)</warning>
        func(1, <warning descr="Unexpected argument">2</warning>)
        """
    );
  }

  public void testMetaclassHavingDunderCall() {
    doTestByText("""
                   from typing import Self
      
      
                   class Meta(type):
                       def call(cls, *args, **kwargs) -> object: ...
                   
                       __call__ = call
                   
                   
                   class MyClass(metaclass=Meta):
                       def __new__(cls, p) -> Self: ...
                   
                   
                   expr = MyClass()
                   """);
    doTestByText("""
                   from typing import Self
                   
                   
                   class Meta(type):
                       def __call__(cls): ...
                   
                   
                   class MyClass(metaclass=Meta):
                       def __new__(cls, p) -> Self: ...
                   
                   
                   c = MyClass(<warning descr="Parameter 'p' unfilled">)</warning>
                   """);
    doTestByText("""
                   from typing import Self
                   
                   
                   class Meta(type):
                       def __call__[T](cls: type[T], *args, **kwargs) -> T: ...
                   
                   
                   class MyClass(metaclass=Meta):
                       def __new__(cls, p) -> Self: ...
                   
                   
                   c = MyClass(<warning descr="Parameter 'p' unfilled">)</warning>
                   """);
    doTestByText("""
                   from typing import Self
                   
                   
                   class Meta(type):
                       def __call__[T](cls, x: T) -> T: ...
                   
                   
                   class MyClass(metaclass=Meta):
                       def __new__(cls, p1, p2) -> Self: ...
                   
                   
                   c = MyClass(1)
                   """);
  }
}
