// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyTypeAliasStatement;

import java.util.List;

public class PyGotoSymbolTest extends PyTestCase {
  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/gotoSymbol/";
  }

  // PY-78476
  public void testTypeAliasStatement() {
    myFixture.configureByFile(getTestName(true) + ".py");
    List<Object> results = myFixture.getGotoSymbolResults("ExampleType", false, null);
    assertEquals(1, results.size());
    assertInstanceOf(results.get(0), PyTypeAliasStatement.class);
  }
}
