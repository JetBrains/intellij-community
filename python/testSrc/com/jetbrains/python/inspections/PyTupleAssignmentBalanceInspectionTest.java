/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

public class PyTupleAssignmentBalanceInspectionTest extends PyInspectionTestCase {

  public void testPy2() {
    doTest();
  }

  public void testPy3() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTest);
  }

  // PY-4357
  public void testPy4357() {
    doTest();
  }

  // PY-4360
  public void testPy4360() {
    doTest();
  }

  // PY-4358
  public void testPy4358() {
    doTest();
  }

  // PY-6315
  public void testPy6315() {
    doTest();
  }

  // PY-22224
  public void testUnpackNonePy2() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, this::doTest);
  }

  // PY-22224
  public void testUnpackNonePy3() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTest);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTupleAssignmentBalanceInspection.class;
  }
}
