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

  public void testStatementListEditInNestedFunction() {
    doTest();
  }

  public void testStatementListEditInNestedIfElse() {
    doTest();
  }

  public void testStatementListEditIn3LevelNesting() {
    doTest();
  }

  public void testStatementListAddInExceptBlock() {
    doTest();
  }

  public void testStatementListAddInFinallyBlock() {
    doTest();
  }

  public void testStatementListEditClassBody() {
    doTest();
  }

  public void testStatementListAddMethodToClass() {
    doTest();
  }

  public void testStatementListReplacePassWithStatement() {
    doTest();
  }

  public void testStatementListEditInWithBody() {
    doTest();
  }

  public void testStatementListEditInsideParentheses() {
    doTest();
  }

  public void testStatementListAddCommentOnlyLine() {
    doTest();
  }

  public void testStatementListEditSameLineIf() {
    doTest();
  }

  public void testStatementListBackslashContinuation() {
    doTest();
  }

  public void testStatementListEditMultilineString() {
    doTest();
  }

  public void testStatementListEditInNestedClass() {
    doTest();
  }

  public void testStatementListEditOuterWithNestedClass() {
    doTest();
  }

  public void testStatementListEditInDeeplyNestedClassMethod() {
    doTest();
  }

  public void testStatementListInsertEntireFunction() {
    doTest();
  }

  public void testStatementListDeleteEntireFunction() {
    doTest();
  }

  public void testStatementListInsertClassIntoClass() {
    doTest();
  }

  public void testStatementListDeleteMultipleStatements() {
    doTest();
  }

  public void testStatementListInsertMultipleFunctions() {
    doTest();
  }

  public void testStatementListDeleteNestedFunctionBody() {
    doTest();
  }

  public void testStatementListEditFunctionFirstStatement() {
    doTest();
  }

  public void testStatementListEditFunctionLastStatement() {
    doTest();
  }

  public void testStatementListEditBetweenMethods() {
    doTest();
  }

  public void testStatementListAddEmptyLineInBlock() {
    doTest();
  }

  public void testStatementListEditNearFunctionDef() {
    doTest();
  }

  public void testStatementListEditAfterDecorator() {
    doTest();
  }

  public void testStatementListDedentDeeplyNestedToZero() {
    doTest(true, false);
  }

  public void testStatementListOverIndentDeeplyNested() {
    doTest(true, false);
  }

  public void testStatementListDedentMiddleOfBlock() {
    doTest(true, false);
  }

  public void testStatementListIndentEntireBlock() {
    doTest(true, false);
  }

  public void testStatementListMoveStatementAcrossBlocks() {
    doTest(true, false);
  }
}
