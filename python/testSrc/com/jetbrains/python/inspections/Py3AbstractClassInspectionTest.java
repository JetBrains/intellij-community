// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Py3AbstractClassInspectionTest extends PyInspectionTestCase {

  // PY-30789
  public void testHiddenForAbstractSubclassWithABCSuperclass() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doMultiFileTest);
  }

  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPyLatestDescriptor;
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyAbstractClassInspection.class;
  }
}
