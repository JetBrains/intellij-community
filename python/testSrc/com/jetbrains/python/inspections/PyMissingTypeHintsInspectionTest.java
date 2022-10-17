// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyMissingTypeHintsInspectionTest extends PyInspectionTestCase {
  public void testPy3kAnnotations() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testNoAnnotations() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testTypeComment() {
    doTest(LanguageLevel.PYTHON27);
  }

  // PY-18877
  public void testTypeCommentOnTheSameLine() {
    doTest(LanguageLevel.PYTHON27);
  }

  // PY-39556
  public void testOverloads() {
    doTestByText("""
                   from typing import overload
                   @overload
                   def test(value: int) -> int:
                       ...
                   @overload
                   def test(value: float) -> float:
                       ...
                   def test(value):
                       return value""");
  }

  private void doTest(LanguageLevel languageLevel) {
    runWithLanguageLevel(languageLevel, this::doTest);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyMissingTypeHintsInspection.class;
  }

  @Override
  protected void configureInspection() {
    PyMissingTypeHintsInspection inspection = new PyMissingTypeHintsInspection();
    inspection.m_onlyWhenTypesAreKnown = false;
    myFixture.enableInspections(inspection);
    myFixture.checkHighlighting(false, false, true);
  }
}
