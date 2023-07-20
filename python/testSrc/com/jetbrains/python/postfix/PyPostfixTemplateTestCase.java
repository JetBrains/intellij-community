// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.postfix;

import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NonNls;

public abstract class PyPostfixTemplateTestCase extends PyTestCase {
  protected void doTest() {
    myFixture.configureByFile(getTestName(true) + ".py");
    myFixture.type("\t");
    myFixture.checkResultByFile(getTestName(true) + "_after" + ".py", true);
  }

  protected void doTest(String input, String expected) {
    myFixture.configureByText("input.py", input);
    myFixture.type("\t");
    myFixture.checkResult(expected, true);
  }

  protected void doSimpleTest(String declaration) {
    doTest("x = %s\nx.foo<caret>\n".formatted(declaration), "x = %s\nfoo(x)\n".formatted(declaration));
  }

  abstract protected String getTestDataDir();

  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/postfix/" + getTestDataDir();
  }
}
