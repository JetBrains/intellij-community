// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstCaseClause;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstPattern;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public final class PyCaseClauseFixer extends PyFixer<PyAstCaseClause> {
  public PyCaseClauseFixer() {
    super(PyAstCaseClause.class);
  }

  @Override
  protected void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyAstCaseClause element) {
    PyAstPattern pattern = element.getPattern();
    PsiElement ifKeyword = PyPsiUtilsCore.getFirstChildOfType(element, PyTokenTypes.IF_KEYWORD);
    PyAstExpression condition = element.getGuardCondition();
    PsiElement colon = PyPsiUtilsCore.getFirstChildOfType(element, PyTokenTypes.COLON);
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
        PsiWhiteSpace prevWhitespace = tryCast(ifKeyword.getPrevSibling(), PsiWhiteSpace.class);
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
