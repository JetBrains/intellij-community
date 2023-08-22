// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.surround.surrounders.statements;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyWithReturnSurrounder extends PyStatementSurrounder {
  private static final @NlsSafe String TEMPLATE_DESCRIPTION = "return";

  @Override
  public boolean isApplicable(PsiElement @NotNull [] elements) {
    return (elements.length == 1) &&
           (elements[0] instanceof PyExpressionStatement) &&
           (PsiTreeUtil.getParentOfType(elements[0], PyFunction.class) != null);
  }

  @Override
  @Nullable
  protected TextRange surroundStatement(@NotNull Project project, @NotNull Editor editor, PsiElement @NotNull [] elements)
    throws IncorrectOperationException {
    PyReturnStatement returnStatement =
      PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyReturnStatement.class, "return a");
    PyExpression expression = returnStatement.getExpression();
    assert expression != null;
    PsiElement element = elements[0];
    expression.replace(element);
    element = element.replace(returnStatement);
    element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element);
    return element.getTextRange();
  }

  @Override
  public String getTemplateDescription() {
    //noinspection DialogTitleCapitalization
    return TEMPLATE_DESCRIPTION;
  }
}
