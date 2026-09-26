// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.postfix;

import com.jetbrains.python.allure.Components;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.psi.LanguageLevel;

@Subsystems.CodeCompletion
@Components.Postfix
@Layers.Functional
public class PyPrintPostfixTemplateTest extends PyPostfixTemplateTestCase {

  public void testNumber2() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, this::doTest);
  }

  public void testNumber3() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testTuple() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  @Override
  protected String getTestDataDir() {
    return "print/";
  }
}
