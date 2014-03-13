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
 * @author yole
 */
public class PyRedundantParenthesesInspectionTest extends PyTestCase {
  public void doTest() {
    myFixture.configureByFile("inspections/PyRedundantParenthesesInspection/" + getTestName(false) + ".py");
    myFixture.enableInspections(PyRedundantParenthesesInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }

  public void doTest(LanguageLevel languageLevel) {
    try {
      setLanguageLevel(languageLevel);
      myFixture.configureByFile("inspections/PyRedundantParenthesesInspection/" + getTestName(false) + ".py");
      myFixture.enableInspections(PyRedundantParenthesesInspection.class);
      myFixture.checkHighlighting(true, false, true);
    } finally {
      setLanguageLevel(null);
    }
  }

  public void testBooleanMultiline() {
    doTest();
  }

  public void testFormatting() {
    doTest();
  }

  public void testIfElif() {
    doTest();
  }

  public void testIfMultiline() {
    doTest();
  }

  public void testStringMultiline() {
    doTest();
  }

  public void testTryExcept() {
    doTest();
  }

  public void testTryExceptNegate() {
    doTest();
  }

  public void testWhile() {
    doTest();
  }

  public void testYieldFrom() {       //PY-7410
    doTest(LanguageLevel.PYTHON33);
  }

  public void testYield() {       //PY-10420
    doTest(LanguageLevel.PYTHON27);
  }

  public void testBinaryInBinary() {       //PY-10420
    doTest();
  }

}
