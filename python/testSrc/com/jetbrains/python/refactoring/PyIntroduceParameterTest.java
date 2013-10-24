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

import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.parameter.PyIntroduceParameterHandler;

/**
 * User: ktisha
 */
@TestDataPath("$CONTENT_ROOT/../testData/refactoring/introduceParameter/")
public class PyIntroduceParameterTest extends PyIntroduceTestCase {
  public void testSimple() {
    doTest();
  }

  public void testReferencedParameter() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"));
  }

  public void testParameter() {
    doTest();
  }

  public void testKwParameter() {
    doTest();
  }

  public void testLastStatement() {
    doTest();
  }

  public void testLocalVariable() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"));
  }

  public void testLocalVariable1() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"));
  }

  public void testLocalVariableParam() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"));
  }

  public void testNonLocal() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"), LanguageLevel.PYTHON32);
  }

  public void testGlobal() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"));
  }

  public void testFunctionDef() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"));
  }


  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceParameter";
  }

  protected IntroduceHandler createHandler() {
    return new PyIntroduceParameterHandler();
  }

  private void doTestCannotPerform(String expected) {
    try {
      doTest();
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(expected, e.getMessage());
    }
  }

  private void doTestCannotPerform(String expected, LanguageLevel languageLevel) {
    setLanguageLevel(languageLevel);
    try {
      doTestCannotPerform(expected);
    }
    finally {
      setLanguageLevel(null);
    }
  }
}
