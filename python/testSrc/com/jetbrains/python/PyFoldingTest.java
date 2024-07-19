// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.folding.impl.EditorFoldingInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.EditorTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyStringLiteralExpression;


public class PyFoldingTest extends PyTestCase {
  protected void doTest() {
    myFixture.testFolding(getTestDataPath() + "/folding/" + getTestName(true) + ".py");
  }

  public void testClassTrailingSpace() {  // PY-2544
    doTest();
  }

  public void testDocString() {
    doTest();
  }

  public void testCustomFolding() {
    doTest();
  }

  public void testImportBlock() {
    doTest();
  }

  public void testBlocksFolding() {
    doTest();
  }

  public void testLongStringsFolding() {
    doTest();
  }

  public void testCollectionsFolding() {
    doTest();
  }

  public void testMultilineComments() {
    doTest();
  }

  public void testNestedFolding() {
    doTest();
  }

  //PY-18928
  public void testCustomFoldingWithComments() {
    doTest();
  }

  // PY-17017
  public void testCustomFoldingAtBlockEnd() {
    doTest();
  }

  // PY-31154
  public void testEmptyStatementListHasNoFolding() {
    doTest();
  }

  public void testCollapseExpandDocCommentsTokenType() {
    myFixture.configureByFile(collapseExpandDocCommentsTokenTypeFile());
    EditorTestUtil.buildInitialFoldingsInBackground(myFixture.getEditor());
    checkCollapseExpand(true);
    checkCollapseExpand(false);
  }

  protected String collapseExpandDocCommentsTokenTypeFile() {
    return getTestDataPath() + "/folding/" + getTestName(true) + ".py";
  }

  private void checkCollapseExpand(boolean doExpand) {
    final String initial = doExpand ? "CollapseAllRegions" : "ExpandAllRegions";
    final String action = doExpand ? "ExpandDocComments" : "CollapseDocComments";
    final String logAction = doExpand ? "collapsed: " : "expanded: ";

    myFixture.performEditorAction(initial);
    myFixture.performEditorAction(action);

    final Editor editor = myFixture.getEditor();
    for (FoldRegion region : editor.getFoldingModel().getAllFoldRegions()) {
      PsiElement element = EditorFoldingInfo.get(editor).getPsiElement(region);
      if (element instanceof PyStringLiteralExpression && ((PyStringLiteralExpression)element).isDocString()) {
        assertEquals(logAction + element.getText(), doExpand, region.isExpanded());
      }
      else {
        assertEquals("not " + logAction + element.getText(), doExpand, !region.isExpanded());
      }
    }
  }

  // PY-39406
  public void testStringPrefixFolding() {
    doTest();
  }

  // PY-49174
  public void testMatchFolding() {
    doTest();
  }
}
