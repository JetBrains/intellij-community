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
public class PyConvertFormatOperatorToMethodIntentionTest extends PyIntentionTestCase {

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy2Descriptor;
  }

  public void testSimple() {
    doTest(PyPsiBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testMulti() {
    doTest(PyPsiBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testEscaped() {
    doTest(PyPsiBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testUnicode() {
    doTest(PyPsiBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  // PY-9176
  public void testConcatenated() {
    doTest(PyPsiBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }
  
  // PY-20752
  public void testTupleReference() {
    doTest(PyPsiBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }
  
  // PY-20798
  public void testDictReference() {
    doTest(PyPsiBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);    
  }

  // PY-20798
  public void testDictCallReference() {
    doTest(PyPsiBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }
  
  // PY-20754
  public void testBytes() {
    doNegativeTest(PyPsiBundle.message("INTN.replace.with.method"));
  }

  // PY-20800
  public void testRepr() {
    doTest(PyPsiBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }
  
  // PY-20803
  public void testStarWidth() {
    doNegativeTest(PyPsiBundle.message("INTN.replace.with.method"));
  }
  
  // PY-20803
  public void testStarPrecision() {
    doNegativeTest(PyPsiBundle.message("INTN.replace.with.method"));
  }

  // PY-20803
  public void testStarWidthPrecision() {
    doNegativeTest(PyPsiBundle.message("INTN.replace.with.method"));    
  }
  
  // PY-20876
  public void testSet() {
    doTest(PyPsiBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }
  
  // PY-20917
  public void testSetCall() {
    doTest(PyPsiBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);    
  }

  // PY-20917
  public void testCallWithNoneReturnType() {
    doTest(PyPsiBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }
  
  public void testDictSubclass() {
    doTest(PyPsiBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);    
  }
}
