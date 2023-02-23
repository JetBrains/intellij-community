// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Py3ArgumentListInspectionTest extends PyInspectionTestCase {
  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyArgumentListInspection.class;
  }

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPyLatestDescriptor;
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
}
