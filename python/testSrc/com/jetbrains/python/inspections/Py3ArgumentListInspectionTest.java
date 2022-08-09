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

  // PY-50404
  public void testPassingKeywordArgumentsToParamSpec() {
    doTestByText("from collections.abc import Callable\n" +
                 "from typing import ParamSpec\n" +
                 "\n" +
                 "P = ParamSpec(\"P\")\n" +
                 "\n" +
                 "\n" +
                 "def changes_return_type_to_str(x: Callable[P, int]) -> Callable[P, str]:\n" +
                 "    def inner(*args: P.args, **kwargs: P.kwargs) -> str:\n" +
                 "        return \"42\"\n" +
                 "    return inner\n" +
                 "\n" +
                 "\n" +
                 "def returns_int(a: str, b: bool) -> int:\n" +
                 "    return 42\n" +
                 "\n" +
                 "\n" +
                 "f = changes_return_type_to_str(returns_int)\n" +
                 "res2 = f(a=\"A\", b=True)");
  }

  // PY-53611
  public void testTypedDictWithRequiredAndNotRequiredKeys() {
    doTestByText("from typing_extensions import TypedDict, Required, NotRequired\n" +
                 "class A(TypedDict):\n" +
                 "    x: int\n" +
                 "    y: NotRequired[int]\n" +
                 "class B(TypedDict, total=False):\n" +
                 "    x: Required[int]\n" +
                 "    y: int\n" +
                 "a = A(<warning descr=\"Parameter 'x' unfilled\">)</warning>\n" +
                 "b = B(<warning descr=\"Parameter 'x' unfilled\">)</warning>");
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
    doTestByText("import random\n" +
                 "\n" +
                 "random.randint(1, 2)");
  }

  // PY-53388
  public void testEnumAuto() {
    doTestByText("import enum\n" +
                 "\n" +
                 "class MyEnum(enum.Enum):\n" +
                 "    FOO = enum.auto()\n" +
                 "    BAR = enum.auto()\n" +
                 "");
  }
}
