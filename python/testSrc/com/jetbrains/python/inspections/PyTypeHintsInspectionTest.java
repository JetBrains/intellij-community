// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyTypeHintsInspectionTest extends PyInspectionTestCase {

  // PY-28243
  public void testTypeVarAndTargetName() {
    doTestByText("from typing import TypeVar\n" +
                 "\n" +
                 "T0 = TypeVar('T0')\n" +
                 "T1 = TypeVar(<warning descr=\"The argument to 'TypeVar()' must be a string equal to the variable name to which it is assigned\">'T2'</warning>)");
  }

  // PY-28243
  public void testTypeVarPlacement() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing import List, TypeVar\n" +
                         "\n" +
                         "T0 = TypeVar('T0')\n" +
                         "a: List[T0]\n" +
                         "b: List[<warning descr=\"A 'TypeVar()' expression must always directly be assigned to a variable\">TypeVar('T1')</warning>]")
    );
  }

  // PY-28243
  public void testTypeVarRedefinition() {
    doTestByText("from typing import TypeVar\n" +
                 "\n" +
                 "T0 = TypeVar('T0')\n" +
                 "print(T0)\n" +
                 "<warning descr=\"Type variables must not be redefined\">T0</warning> = TypeVar('T0')");
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTypeHintsInspection.class;
  }
}
