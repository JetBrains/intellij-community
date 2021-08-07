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

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */
public class PyStringConcatenationToFormatIntentionTest extends PyIntentionTestCase {

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy2Descriptor;
  }

  public void testSimple() {
    doTest(PyPsiBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON26);
  }

  public void testAugmentAssignment() {   //PY-5226
    doTest(PyPsiBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON26);
  }

  public void testNegative() {   //PY-6505
    runWithLanguageLevel(LanguageLevel.PYTHON26, () -> doNegativeTest(PyPsiBundle.message("INTN.replace.plus.with.format.operator")));
  }

  public void testTwoStrings() {   //PY-6505
    doTest(PyPsiBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON26);
  }

  public void testUnknownType() {   //PY-7969
    doNegativeTest(PyPsiBundle.message("INTN.replace.plus.with.format.operator"));
  }

  public void testEmptyStrings() {   //PY-7968
    doNegativeTest(PyPsiBundle.message("INTN.replace.plus.with.format.operator"));
  }

  public void testUnicodeString() { //PY-7463
    doTest(PyPsiBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON26);
  }

  public void testUnicodeSecondString() { //PY-7463
    doTest(PyPsiBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON26);
  }

  // PY-8366
  public void testEscapingPy3() {
    doTest(PyPsiBundle.message("INTN.replace.plus.with.str.format"), LanguageLevel.PYTHON34);
  }

  // PY-8588
  public void testEscaping() {
    doTest(PyPsiBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON26);
  }

  public void testPy3() {   //PY-4706
    doTest(PyPsiBundle.message("INTN.replace.plus.with.str.format"), LanguageLevel.PYTHON34);
  }

  public void testPy3Unicode() {
    doTest(PyPsiBundle.message("INTN.replace.plus.with.str.format"), LanguageLevel.PYTHON34);
  }
}
