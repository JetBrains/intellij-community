// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.editor.selectWord;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public final class PyStatementSelectionHandler extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull final PsiElement e) {
    return e instanceof PyStringLiteralExpression || e instanceof PyCallExpression || e instanceof PyStatement ||
           e instanceof PyStatementList;
  }

  @Override
  public List<TextRange> select(@NotNull final PsiElement e, @NotNull final CharSequence editorText, final int cursorOffset, @NotNull final Editor editor) {
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
