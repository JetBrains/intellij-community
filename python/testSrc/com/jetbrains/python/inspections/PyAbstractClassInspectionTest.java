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

public class PyAbstractClassInspectionTest extends PyInspectionTestCase {

  public void testAbstract() {
    doTest();
  }

  public void testOverriddenAsField() {
    doTest();
  }

  public void testSuperMethodRaisesNotImplementerError() {
    doTest();
  }

  // PY-16035
  public void testHiddenForAbstractSubclassWithExplicitMetaclass() {
    doTest();
  }

  // PY-16035
  public void testHiddenForAbstractSubclassWithExplicitMetaclassPy3() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, () -> doTest());
  }

  // PY-16035
  public void testHiddenForAbstractSubclassWithAbstractMethod() {
    doTest();
  }

  // PY-16776
  public void testNotImplementedOverriddenInParent() {
    doTest();
  }

  public void testConditionalRaiseReturnInIfPart() {
    doTest();
  }

  public void testConditionalRaiseReturnInElsePart() {
    doTest();
  }

  public void testConditionalRaiseNestedIfs() {
    doTest();
  }

  public void testConditionalRaiseReturnInElifPart() {
    doTest();
  }

  // PY-25624
  public void testConditionalRaiseNoReturn() {
    doTest();
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyAbstractClassInspection.class;
  }
}
