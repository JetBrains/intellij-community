// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.folding.CustomFoldingSurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.refactoring.surround.surrounders.statements.PyWithIfSurrounder;
import com.jetbrains.python.refactoring.surround.surrounders.statements.PyWithTryExceptSurrounder;
import com.jetbrains.python.refactoring.surround.surrounders.statements.PyWithWhileSurrounder;


public class PySurroundWithTest extends PyTestCase {
  public void testSurroundWithIf() {
    doTest(new PyWithIfSurrounder());
  }

  public void testSurroundWithWhile() {
    doTest(new PyWithWhileSurrounder());
  }

  public void testSurroundWithTryExcept() {
    doTest(new PyWithTryExceptSurrounder());
  }

  // PY-11357
  public void testCustomFoldingRegionFirstMethod() {
    doTestSurroundWithCustomFoldingRegion();
  }

  // PY-11357
  public void testCustomFoldingRegionLastMethod() {
    doTestSurroundWithCustomFoldingRegion();
  }

  // PY-14261
  public void testCustomFoldingRegionPreservesIndentation() {
    doTestSurroundWithCustomFoldingRegion();
  }

  public void testCustomFoldingRegionSingleCharacter() {
    doTestSurroundWithCustomFoldingRegion();
  }

  public void testCustomFoldingRegionSingleStatementInFile() {
    doTestSurroundWithCustomFoldingRegion();
  }

  public void testCustomFoldingRegionIllegalSelection() {
    doTestSurroundWithCustomFoldingRegion();
  }

  public void testCustomFoldingRegionSeveralMethods() {
    doTestSurroundWithCustomFoldingRegion();
  }

  private void doTestSurroundWithCustomFoldingRegion() {
    final Surrounder surrounder = ContainerUtil.find(CustomFoldingSurroundDescriptor.getAllSurrounders(),
                                                     (Condition<Surrounder>)surrounder1 -> surrounder1.getTemplateDescription().contains("<editor-fold"));
    assertNotNull(surrounder);
    doTest(surrounder);
  }

  public void testSurroundCommentAtStart() {
    doTest(new PyWithIfSurrounder());
  }

  public void testSurroundCommentAtEnd() {
    doTest(new PyWithIfSurrounder());
  }

  public void testSurroundNewline() {
    doTest(new PyWithIfSurrounder());
  }

  private void doTest(final Surrounder surrounder) {
    String baseName = "/surround/" + getTestName(false);
    myFixture.configureByFile(baseName + ".py");
    SurroundWithHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder);
    myFixture.checkResultByFile(baseName + "_after.py", true);
  }
}
