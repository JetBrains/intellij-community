// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.surround.surrounders.expressions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

public class PyWithParenthesesSurrounder extends PyExpressionSurrounder {
  @Override
  public String getTemplateDescription() {
    //noinspection DialogTitleCapitalization
    return CodeInsightBundle.message("surround.with.parenthesis.template");
  }

  @Override
  public boolean isApplicable(@NotNull PyExpression elements) {
    return true;
  }

  @Override
  public TextRange surroundExpression(@NotNull Project project, @NotNull Editor editor, @NotNull PyExpression element)
    throws IncorrectOperationException {
    PyParenthesizedExpression parenthesesExpression = (PyParenthesizedExpression)PyElementGenerator.getInstance(project)
      .createFromText(LanguageLevel.getDefault(), PyExpressionStatement.class, "(a)").getExpression();
    PyExpression expression = parenthesesExpression.getContainedExpression();
    assert expression != null;
    expression.replace(element);
    element = (PyExpression) element.replace(parenthesesExpression);
    element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element);
    return element.getTextRange();
  }
}
