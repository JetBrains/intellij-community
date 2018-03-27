// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public class PyUnconditionalStatementPartFixer extends PyFixer<PyElement> {
  public PyUnconditionalStatementPartFixer() {
    super(PyElement.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyElement psiElement)
    throws IncorrectOperationException {
    if (PsiTreeUtil.instanceOf(psiElement, PyElsePart.class, PyTryPart.class, PyFinallyPart.class)) {
      final PsiElement colon = PyPsiUtils.getFirstChildOfType(psiElement, PyTokenTypes.COLON);
      if (colon == null) {
        final TokenSet keywords = TokenSet.create(PyTokenTypes.ELSE_KEYWORD, PyTokenTypes.TRY_KEYWORD, PyTokenTypes.FINALLY_KEYWORD);
        final PsiElement keywordToken = PyPsiUtils.getChildByFilter(psiElement, keywords, 0);
        editor.getDocument().insertString(sure(keywordToken).getTextRange().getEndOffset(), ":");
      }
    }
  }
}
