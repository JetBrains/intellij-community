// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import com.jetbrains.python.PythonTestUtil;

public class PythonIncrementalParsingTest extends PythonIncrementalParsingTestCase {

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/psi/incrementalParsing";
  }

  public void testStatementListAddElement() {
    doTest();
  }

  public void testStatementListRemoveElement() {
    doTest();
  }

  public void testStatementListTypeInTryExcept() {
    doTest();
  }

  public void testStatementListAddElementOnParentIndentationLevel() {
    doTest(true, false);
  }

  public void testStatementListAddElementWithUnexpectedIndent() {
    doTest(true, false);
  }

  public void testStatementListLeaveStatementListEmpty() {
    doTest(true, false);
  }

  public void testStatementListNoReparseAfterRestoringIndentationLevel() {
    doTest(false, true);
  }

  public void testStatementListOnTheSameLineAfterColon() {
    doTest();
  }

  public void testStatementListReplaceMethodWithMultipleMethods() {
    doTest();
  }

  public void testStatementListMoveMethodToParentIndentationLevel() {
    doTest();
  }

  public void testStatementListMoveMethodToChildIndentationLevel() {
    doTest();
  }
}
