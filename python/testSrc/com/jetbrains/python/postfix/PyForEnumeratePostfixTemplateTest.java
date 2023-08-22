// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.postfix;

public class PyForEnumeratePostfixTemplateTest extends PyPostfixTemplateTestCase{

  public void testTopLevel() {
    doTest();
  }

  public void testFunction() {
    doTest();
  }

  public void testComplexExpression() {
    doTest();
  }

  @Override
  protected String getTestDataDir() {
    return "fore/";
  }
}
