// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author link
 */
public class PyAssignmentToLoopOrWithParameterInspectionTest extends PyInspectionTestCase {

  public void testGood() {
    doTest();
  }

  public void testBad() {
    doTest();
  }

  // PY-26137
  public void testRedeclaredUnderscore() {
    doTest();
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyAssignmentToLoopOrWithParameterInspection.class;
  }
}
