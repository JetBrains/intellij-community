// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTest);
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
