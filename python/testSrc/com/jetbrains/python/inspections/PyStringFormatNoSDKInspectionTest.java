// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyStringFormatNoSDKInspectionTest extends PyInspectionTestCase {

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR;
  }

  // PY-80147
  public void testTupleFormat() {
    doTest();
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyStringFormatInspection.class;
  }

  @Override
  protected boolean isLowerCaseTestFile() {
    return false;
  }
}
