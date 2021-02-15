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
package com.jetbrains.python;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.folding.impl.EditorFoldingInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyStringLiteralExpression;

/**
 * @author yole
 */
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
    CodeFoldingManager.getInstance(myFixture.getProject()).buildInitialFoldings(myFixture.getEditor());
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
}
