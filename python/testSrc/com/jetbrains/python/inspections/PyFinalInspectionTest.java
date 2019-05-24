// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
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

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyFinalInspection.class;
  }
}
