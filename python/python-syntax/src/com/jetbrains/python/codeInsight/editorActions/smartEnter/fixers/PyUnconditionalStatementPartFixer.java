// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstElement;
import com.jetbrains.python.ast.PyAstElsePart;
import com.jetbrains.python.ast.PyAstFinallyPart;
import com.jetbrains.python.ast.PyAstTryPart;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PyUnconditionalStatementPartFixer extends PyFixer<PyAstElement> {
  public PyUnconditionalStatementPartFixer() {
    super(PyAstElement.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyAstElement psiElement)
    throws IncorrectOperationException {
    if (PsiTreeUtil.instanceOf(psiElement, PyAstElsePart.class, PyAstTryPart.class, PyAstFinallyPart.class)) {
      final PsiElement colon = PyPsiUtilsCore.getFirstChildOfType(psiElement, PyTokenTypes.COLON);
      if (colon == null) {
        final TokenSet keywords = TokenSet.create(PyTokenTypes.ELSE_KEYWORD, PyTokenTypes.TRY_KEYWORD, PyTokenTypes.FINALLY_KEYWORD);
        final PsiElement keywordToken = PyPsiUtilsCore.getChildByFilter(psiElement, keywords, 0);
        editor.getDocument().insertString(Objects.requireNonNull(keywordToken).getTextRange().getEndOffset(), ":");
      }
    }
  }
}
