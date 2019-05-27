// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyFinalInspectionTest extends PyInspectionTestCase {

  // PY-34945
  public void testSubclassingFinalClass() {
    doMultiFileTest();

    doTestByText("from typing_extensions import final\n" +
                 "@final\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "class B(<warning descr=\"'A' is marked as '@final' and should not be subclassed\">A</warning>):\n" +
                 "    pass");
  }

  // PY-34945
  public void testOverridingFinalMethod() {
    doMultiFileTest();

    doTestByText("from typing_extensions import final\n" +
                 "class C:\n" +
                 "    @final\n" +
                 "    def method(self):\n" +
                 "        pass\n" +
                 "class D(C):\n" +
                 "    def <warning descr=\"'aaa.C.method' is marked as '@final' and should not be overridden\">method</warning>(self):\n" +
                 "        pass");
  }

  // PY-34945
  public void testOverridingOverloadedFinalMethod() {
    doMultiFileTest();

    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("from typing_extensions import final\n" +
                         "from typing import overload\n" +
                         "\n" +
                         "class A:\n" +
                         "    @overload\n" +
                         "    def foo(self, a: int) -> int: ...\n" +
                         "\n" +
                         "    @overload\n" +
                         "    def foo(self, a: str) -> str: ...\n" +
                         "\n" +
                         "    @final\n" +
                         "    def foo(self, a):\n" +
                         "        return a\n" +
                         "\n" +
                         "class B(A):\n" +
                         "    def <warning descr=\"'aaa.A.foo' is marked as '@final' and should not be overridden\">foo</warning>(self, a):\n" +
                         "        return super().foo(a)")
    );
  }

  // PY-34945
  public void testFinalNonMethodFunction() {
    doTestByText("from typing_extensions import final\n" +
                 "@final\n" +
                 "def <warning descr=\"Non-method function could not be marked as '@final'\">foo</warning>():\n" +
                 "    pass");
  }

  // PY-34945
  public void testOmittedAssignedValue() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "a: <warning descr=\"If assigned value is omitted, there should be an explicit type argument to 'Final'\">Final</warning>\n" +
                         "b: Final[int]\n" +
                         "\n" +
                         "MY_FINAL = Final\n" +
                         "MY_FINAL_INT = Final[int]\n" +
                         "\n" +
                         "с: <warning descr=\"If assigned value is omitted, there should be an explicit type argument to 'Final'\">MY_FINAL</warning>\n" +
                         "в: MY_FINAL_INT")
    );
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyFinalInspection.class;
  }
}
