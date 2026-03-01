// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.surround.surrounders.statements;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyWithIfSurrounder extends PyStatementSurrounder {
  @Override
  protected @Nullable TextRange surroundStatement(@NotNull Project project, @NotNull Editor editor, PsiElement @NotNull [] elements)
    throws IncorrectOperationException {
    PyIfStatement ifStatement = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyIfStatement.class, "if True:\n    ");
    final PsiElement parent = elements[0].getParent();
    final PyStatementList statementList = ifStatement.getIfPart().getStatementList();
    statementList.addRange(elements[0], elements[elements.length - 1]);
    ifStatement = (PyIfStatement) parent.addBefore(ifStatement, elements[0]);
    parent.deleteChildRange(elements[0], elements[elements.length - 1]);

    ifStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(ifStatement);
    if (ifStatement == null) {
      return null;
    }
    final PyExpression condition = ifStatement.getIfPart().getCondition();
    return condition != null ? condition.getTextRange() : null;
  }

  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.if.template");
  }
}
