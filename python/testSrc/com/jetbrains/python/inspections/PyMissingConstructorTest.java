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

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PyMissingConstructorTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyMissingConstructorInspection/";

  public void testBasic() {
    doTest();
  }

  // PY-3278
  public void testQualifiedName() {
    doTest();
  }

  // PY-3238
  public void testNoConstructor() {
    doTest();
  }

  // PY-3313
  public void testDeepInheritance() {
    doTest();
  }

  // PY-3395
  public void testInheritFromSelf() {
    doTest();
  }

  // PY-4038
  public void testExplicitDunderClass() {
    doTest();
  }

  // PY-20038
  public void testImplicitDunderClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  // PY-7176
  public void testException() {
    doTest();
  }

  // PY-7699
  public void testInnerClass() {
    doTest();
  }

  public void testPy3k() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  private void doTest() {
    myFixture.configureByFile(TEST_DIRECTORY + getTestName(true) + ".py");
    myFixture.enableInspections(PyMissingConstructorInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
