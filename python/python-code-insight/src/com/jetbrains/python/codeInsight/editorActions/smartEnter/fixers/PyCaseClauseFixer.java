// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyCaseClause;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.as;

public final class PyCaseClauseFixer extends PyFixer<PyCaseClause> {
  public PyCaseClauseFixer() {
    super(PyCaseClause.class);
  }

  @Override
  protected void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyCaseClause element) {
    PyPattern pattern = element.getPattern();
    PsiElement ifKeyword = PyPsiUtils.getFirstChildOfType(element, PyTokenTypes.IF_KEYWORD);
    PyExpression condition = element.getGuardCondition();
    PsiElement colon = PyPsiUtils.getFirstChildOfType(element, PyTokenTypes.COLON);
    Document document = editor.getDocument();
    int colonOffset;
    if (colon == null) {
      String colonSuffix;
      if (condition != null) {
        colonSuffix = ":";
        colonOffset = condition.getTextRange().getEndOffset();
      }
      else if (ifKeyword != null) {
        colonSuffix = " :";
        colonOffset = ifKeyword.getTextRange().getEndOffset() + 1;
      }
      else if (pattern != null) {
        colonSuffix = ":";
        colonOffset = pattern.getTextRange().getEndOffset();
      }
      else {
        colonSuffix = " :";
        colonOffset = element.getFirstChild().getTextRange().getEndOffset() + 1;
      }
      document.insertString(colonOffset - (colonSuffix.length() - 1), colonSuffix);
    }
    else {
      colonOffset = colon.getTextOffset();
    }
    
    if (pattern == null) {
      if (ifKeyword != null) {
        int ifOffset = ifKeyword.getTextOffset();
        PsiWhiteSpace prevWhitespace = as(ifKeyword.getPrevSibling(), PsiWhiteSpace.class);
        if (prevWhitespace != null && prevWhitespace.getTextLength() < 2) {
          document.insertString(ifOffset, " ");
        }
        processor.registerUnresolvedError(ifOffset);
      }
      else {
        processor.registerUnresolvedError(colonOffset);
      }
    }
    else if (ifKeyword != null && condition == null) {
      if (ifKeyword.getTextRange().getEndOffset() == colonOffset) {
        document.insertString(colonOffset, " ");
        processor.registerUnresolvedError(colonOffset + 1);
      }
      else {
        processor.registerUnresolvedError(colonOffset);
      }
    }
  }
}
