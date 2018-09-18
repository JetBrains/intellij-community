// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.surround.surrounders.expressions;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

public abstract class PyExpressionSurrounder implements Surrounder {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.refactoring.surround.surrounders.expressions.PyExpressionSurrounder");

  @Override
  public boolean isApplicable(@NotNull PsiElement[] elements) {
    LOG.assertTrue(elements.length == 1 && elements[0] instanceof PyExpression);
    return isApplicable((PyExpression)elements[0]);
  }

  public abstract boolean isApplicable(@NotNull final PyExpression expr);

  public abstract TextRange surroundExpression(@NotNull Project project, @NotNull Editor editor, @NotNull PyExpression element)
    throws IncorrectOperationException;

  @Override
  public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements)
    throws IncorrectOperationException {
    return surroundExpression(project, editor, (PyExpression)elements[0]);
  }
}
