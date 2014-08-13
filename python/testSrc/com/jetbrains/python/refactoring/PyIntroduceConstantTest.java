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
package com.jetbrains.python.refactoring;

import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.constant.PyIntroduceConstantHandler;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/refactoring/introduceConstant/")
public class PyIntroduceConstantTest extends PyIntroduceTestCase {
  public void testPy1840() {
    doTest();
  }

  public void testPy1840EntireLine() {
    setLanguageLevel(LanguageLevel.PYTHON30);
    try {
      doTest();
    }
    finally {
      setLanguageLevel(null);
    }
  }

  public void testInsertAfterImport() {  // PY-2149
    doTest();
  }

  public void testInsertAfterDocstring() { // PY-3657
    doTest();
  }

  public void testSuggestUniqueNames() {  // PY-4409
    doTestSuggestions(PyExpression.class, "S1");
  }

  public void testSuggestUniqueNamesGlobalScope() {  // PY-4409
    doTestSuggestions(PyExpression.class, "S1");
  }

  public void testPy4414() {
    doTestInplace(null);
  }

  // PY-13484
  public void testFromParameterDefaultValue() {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceConstant";
  }

  @Override
  protected IntroduceHandler createHandler() {
    return new PyIntroduceConstantHandler();
  }
}
