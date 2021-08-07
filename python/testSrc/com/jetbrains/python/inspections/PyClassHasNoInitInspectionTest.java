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

public class PyClassHasNoInitInspectionTest extends PyInspectionTestCase {

  public void testClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, this::doTest);
  }

  public void testTrueNegative() {
    doTest();
  }

  public void testParentClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, this::doTest);
  }

  public void testInitInParentClass() {
    doTest();
  }

  public void testUnresolvedParent() {
    doTest();
  }

  public void testNew() {
    doTest();
  }

  public void testMeta() {
    doTest();
  }

  public void testUnresolvedAncestor() {
    doTest();
  }

  // PY-24436
  public void testAInheritsBAndBInheritsImportedAWithDunderInit() {
    doMultiFileTest();
  }

  // PY-36008
  public void testTypedDict() {
    doTest();
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyClassHasNoInitInspection.class;
  }
}
