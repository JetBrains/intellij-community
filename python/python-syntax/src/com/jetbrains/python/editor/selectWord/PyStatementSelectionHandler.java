// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.editor.selectWord;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.ast.PyAstCallExpression;
import com.jetbrains.python.ast.PyAstStatement;
import com.jetbrains.python.ast.PyAstStatementList;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public final class PyStatementSelectionHandler extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(final @NotNull PsiElement e) {
    return e instanceof PyAstStringLiteralExpression || e instanceof PyAstCallExpression || e instanceof PyAstStatement ||
           e instanceof PyAstStatementList;
  }

  @Override
  public List<TextRange> select(final @NotNull PsiElement e, final @NotNull CharSequence editorText, final int cursorOffset, final @NotNull Editor editor) {
    PsiElement endElement = e;
    while(endElement.getLastChild() != null) {
      endElement = endElement.getLastChild();
    }
    if (endElement instanceof PsiWhiteSpace) {
      final PsiElement prevSibling = endElement.getPrevSibling();
      if (prevSibling != null) {
        endElement = prevSibling;
      }
    }

    return new ArrayList<>(expandToWholeLine(editorText, new TextRange(e.getTextRange().getStartOffset(),
                                                                       endElement.getTextRange().getEndOffset())));
  }
}
