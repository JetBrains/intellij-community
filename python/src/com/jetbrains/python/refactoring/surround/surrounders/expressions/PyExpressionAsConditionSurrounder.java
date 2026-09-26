// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.surround.surrounders.expressions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStatementListContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PyExpressionAsConditionSurrounder extends PyExpressionSurrounder {

  protected abstract String getTextToGenerate();

  protected abstract @Nullable PyExpression getCondition(PyStatement statement);

  protected abstract @Nullable PyStatementListContainer getStatementListContainer(PyStatement statement);

  @Override
  public TextRange surroundExpression(@NotNull Project project, @NotNull Editor editor, @NotNull PyExpression expression)
    throws IncorrectOperationException {
    TextRange currentCaretPosition = TextRange.from(editor.getCaretModel().getOffset(), 0);
    PyStatement statement = PyElementGenerator.getInstance(project).
      createFromText(LanguageLevel.forElement(expression), PyStatement.class, getTextToGenerate());
    final PyExpression condition = getCondition(statement);
    if (condition == null) {
      return currentCaretPosition;
    }
    condition.replace(expression);
    statement = (PyStatement)CodeStyleManager.getInstance(project).reformat(statement);
    statement = (PyStatement)expression.getParent().replace(statement);
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    PyStatementListContainer statementListContainer = getStatementListContainer(statement);
    if (statementListContainer == null) {
      return currentCaretPosition;
    }
    PyStatementList statementList = statementListContainer.getStatementList();
    PyStatement[] statements = statementList.getStatements();
    if (statements.length == 0) {
      return currentCaretPosition;
    }
    final TextRange range = statements[0].getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    return TextRange.from(range.getStartOffset(), 0);
  }

  @Override
  public boolean isApplicable(@NotNull PyExpression expr) {
    return true;
  }
}
