/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public abstract class IntroduceHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.IntroduceHandlerBase");

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    LOG.assertTrue(elements.length >= 1 && elements[0] instanceof PsiExpression, "incorrect invoke() parameters");
    final PsiElement tempExpr = elements[0];
    final Editor editor;
    if (dataContext != null) {
      final Editor editorFromDC = DataKeys.EDITOR.getData(dataContext);
      final PsiFile cachedPsiFile = PsiDocumentManager.getInstance(project).getCachedPsiFile(editorFromDC.getDocument());
      if (cachedPsiFile != null && PsiTreeUtil.isAncestor(cachedPsiFile, tempExpr, false)) {
        editor = editorFromDC;
      }
      else {
        editor = null;
      }
    }
    else {
      editor = null;
    }
    if (tempExpr instanceof PsiExpression) {
      invokeImpl(project, (PsiExpression)tempExpr, editor);
    }
    else if(tempExpr instanceof PsiLocalVariable) {
      invokeImpl(project, (PsiLocalVariable)tempExpr, editor);
    }
    else {
      LOG.assertTrue(false, "elements[0] should be PsiExpression or PsiLocalVariable");
    }
  }

  /**
   * @param project
   * @param tempExpr
   * @param editor editor to highlight stuff in. Should accept <code>null</code>
   * @return
   */
  protected abstract boolean invokeImpl(Project project, PsiExpression tempExpr,
                                        Editor editor);

  /**
   * @param project
   * @param localVariable
   * @param editor editor to highlight stuff in. Should accept <code>null</code>
   * @return
   */
  protected abstract boolean invokeImpl(Project project, PsiLocalVariable localVariable,
                                        Editor editor);
}
