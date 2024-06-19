// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.surround.surrounders.statements;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PyStatementSurrounder implements Surrounder {
  private static final Logger LOG = Logger.getInstance(PyStatementSurrounder.class);

  @Override
  public boolean isApplicable(PsiElement @NotNull [] elements) {
    return true;
  }

  protected abstract @Nullable TextRange surroundStatement(@NotNull Project project, @NotNull Editor editor, PsiElement @NotNull [] elements)
    throws IncorrectOperationException;

  @Override
  public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, PsiElement @NotNull [] elements) {
    return surroundStatement(project, editor, elements);
  }
}
