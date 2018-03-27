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

public class PyRedeclarationInspectionTest extends PyInspectionTestCase {

  public void testRedeclaredClass() {
    doTest();
  }

  public void testRedeclaredFunction() {
    doTest();
  }

  public void testRedeclaredTopLevel() {
    doTest();
  }

  public void testDecoratedFunction() {
    doTest();
  }

  public void testLocalVariable() {
    doTest();
  }

  public void testConditional() {
    doTest();
  }

  public void testWhile() {
    doTest();
  }

  public void testFor() {
    doTest();
  }

  public void testForAndFunctionBefore() {
    doTest();
  }

  public void testForBody() {
    doTest();
  }

  public void testNestedComprehension() {
    doTest();
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementation() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-22971
  public void testOverloadsAndImplementationInClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementations() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-22971
  public void testOverloadsAndImplementationsInClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-22971
  public void testTopLevelOverloadImplementationOverloadImplementation() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-22971
  public void testOverloadImplementationOverloadImplementationInClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-18375, PY-24408
  public void testSingleUnderscore() {
    doTest();
  }

  // PY-3996
  public void testUnderscorePrefixed() {
    doTest();
  }

  // PY-23552
  public void testStaticMethodRedeclaresInstanceMethod() {
    doTest();
  }

  // PY-23552
  public void testClassMethodRedeclaresInstanceMethod() {
    doTest();
  }

  // PY-23552
  public void testStaticMethodRedeclaresAnotherStaticMethod() {
    doTest();
  }

  // PY-23552
  public void testClassMethodRedeclaresAnotherClassMethod() {
    doTest();
  }

  // PY-26591
  public void testQualifiedTarget() {
    doTest();
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyRedeclarationInspection.class;
  }
}
