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

  // PY-28124
  public void testTypeVarBivariant() {
    doTestByText("from typing import TypeVar\n" +
                 "\n" +
                 "T1 = <error descr=\"Bivariant type variables are not supported\">TypeVar('T1', contravariant=True, covariant=True)</error>\n" +
                 "true = True\n" +
                 "T2 = <error descr=\"Bivariant type variables are not supported\">TypeVar('T2', contravariant=true, covariant=true)</error>");
  }

  // PY-28124
  public void testTypeVarConstraintsAndBound() {
    doTestByText("from typing import TypeVar\n" +
                 "\n" +
                 "T2 = <error descr=\"Constraints cannot be combined with bound=...\">TypeVar('T2', int, str, bound=str)</error>");
  }

  // PY-28124
  public void testTypeVarConstraintsNumber() {
    doTestByText("from typing import TypeVar\n" +
                 "\n" +
                 "T1 = <error descr=\"A single constraint is not allowed\">TypeVar('T1', int)</error>\n" +
                 "T2 = TypeVar('T2', int, str)");
  }

  // PY-28124
  public void testTypeVarNameAsLiteral() {
    doTestByText("from typing import TypeVar\n" +
                 "\n" +
                 "name = 'T0'\n" +
                 "T0 = TypeVar(<warning descr=\"'TypeVar()' expects a string literal as first argument\">name</warning>)\n" +
                 "T1 = TypeVar('T1')");
  }

  // PY-28243
  public void testTypeVarParameterizedConstraints() {
    doTestByText("from typing import TypeVar, List\n" +
                 "\n" +
                 "T1 = TypeVar('T1', int, str)\n" +
                 "\n" +
                 "T2 = TypeVar('T2', int, <warning descr=\"Constraints cannot be parametrized by type variables\">List[T1]</warning>)\n" +
                 "T3 = TypeVar('T3', bound=<warning descr=\"Constraints cannot be parametrized by type variables\">List[T1]</warning>)\n" +
                 "\n" +
                 "T4 = TypeVar('T4', int, List[int])\n" +
                 "T5 = TypeVar('T5', bound=List[int])\n" +
                 "\n" +
                 "my_int = int\n" +
                 "my_str = str\n" +
                 "T11 = TypeVar('T11', my_int, my_str)\n" +
                 "\n" +
                 "my_list_t1 = List[T1]\n" +
                 "T22 = TypeVar('T22', int, <warning descr=\"Constraints cannot be parametrized by type variables\">my_list_t1</warning>)\n" +
                 "T33 = TypeVar('T33', bound=<warning descr=\"Constraints cannot be parametrized by type variables\">my_list_t1</warning>)\n" +
                 "\n" +
                 "my_list_int = List[int]\n" +
                 "T44 = TypeVar('T44', int, my_list_int)\n" +
                 "T55 = TypeVar('T55', bound=my_list_int)");
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTypeHintsInspection.class;
  }
}
