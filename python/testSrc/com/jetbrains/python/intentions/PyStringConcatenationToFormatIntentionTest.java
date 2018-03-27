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
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * User : ktisha
 */
public class PyStringConcatenationToFormatIntentionTest extends PyIntentionTestCase {

  public void testSimple() {
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON26);
  }

  public void testAugmentAssignment() {   //PY-5226
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON26);
  }

  public void testNegative() {   //PY-6505
    runWithLanguageLevel(LanguageLevel.PYTHON26, () -> doNegativeTest(PyBundle.message("INTN.replace.plus.with.format.operator")));
  }

  public void testTwoStrings() {   //PY-6505
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON26);
  }

  public void testUnknownType() {   //PY-7969
    doNegativeTest(PyBundle.message("INTN.replace.plus.with.format.operator"));
  }

  public void testEmptyStrings() {   //PY-7968
    doNegativeTest(PyBundle.message("INTN.replace.plus.with.format.operator"));
  }

  public void testUnicodeString() { //PY-7463
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON26);
  }

  public void testUnicodeSecondString() { //PY-7463
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON26);
  }

  // PY-8366
  public void testEscapingPy3() {
    doTest(PyBundle.message("INTN.replace.plus.with.str.format"), LanguageLevel.PYTHON33);
  }

  // PY-8588
  public void testEscaping() {
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON26);
  }

  public void testPy3() {   //PY-4706
    doTest(PyBundle.message("INTN.replace.plus.with.str.format"), LanguageLevel.PYTHON33);
  }

  public void testPy3Unicode() {
    doTest(PyBundle.message("INTN.replace.plus.with.str.format"), LanguageLevel.PYTHON33);
  }
}