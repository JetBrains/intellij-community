// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;


public class PyPropertyAccessInspectionTest extends PyInspectionTestCase {
  public void testTest() {
    doTest();
  }

  // PY-2313
  public void testOverrideAssignment() {
    doTest();
  }

  // PY-20322
  public void testAbcAbstractProperty() {
    doTest();
  }

  // PY-28206
  public void testSlotOverridesProperty() {
    doTestByText(
      "class A(object):\n" +
      "    @property\n" +
      "    def name(self):\n" +
      "        return 'a'\n" +
      "\n" +
      "class B(A):\n" +
      "    __slots__ = ('name',)\n" +
      "\n" +
      "    def __init__(self, name):\n" +
      "        self.name = name"
    );
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyPropertyAccessInspection.class;
  }
}
