/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
 * User : catherine
 */
public class PyOldStyleClassInspectionTest extends PyTestCase {

  public void testSlot() {
    doTest(getTestName(false));
  }

  public void testGetattr() {
    doTest(getTestName(false));
  }

  public void testSuper() {
    doTest(getTestName(false));
  }

  public void testSuper30() {
    setLanguageLevel(LanguageLevel.PYTHON30);
    doTest(getTestName(false));
  }

  private void doTest(String name) {
    myFixture.configureByFile("inspections/PyOldStyleClassesInspection/" + name + ".py");
    myFixture.enableInspections(PyOldStyleClassesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
