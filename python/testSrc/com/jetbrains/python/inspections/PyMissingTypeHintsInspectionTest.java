/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
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
