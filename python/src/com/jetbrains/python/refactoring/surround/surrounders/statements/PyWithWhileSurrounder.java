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
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyWhileStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyWithWhileSurrounder extends PyStatementSurrounder{
  @Override
  protected @Nullable TextRange surroundStatement(@NotNull Project project, @NotNull Editor editor, PsiElement @NotNull [] elements)
    throws IncorrectOperationException {
    PyWhileStatement whileStatement =
      PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyWhileStatement.class, "while True:\n    ");
    final PsiElement parent = elements[0].getParent();
    final PyStatementList statementList = whileStatement.getWhilePart().getStatementList();
    statementList.addRange(elements[0], elements[elements.length - 1]);
    whileStatement = (PyWhileStatement) parent.addBefore(whileStatement, elements[0]);
    parent.deleteChildRange(elements[0], elements[elements.length - 1]);

    whileStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(whileStatement);
    if (whileStatement == null) {
      return null;
    }
    final PyExpression condition = whileStatement.getWhilePart().getCondition();
    assert condition != null;
    return condition.getTextRange();
  }

  @Override
  public String getTemplateDescription() {
    //noinspection DialogTitleCapitalization
    return CodeInsightBundle.message("surround.with.while.template");
  }
}
