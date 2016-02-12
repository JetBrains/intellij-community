/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.surround.surrounders.expressions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PyExpressionAsConditionSurrounder extends PyExpressionSurrounder {

  protected abstract String getTextToGenerate();

  @Nullable
  protected abstract PyExpression getCondition(PyStatement statement);

  @Nullable
  protected abstract PyStatementListContainer getStatementListContainer(PyStatement statement);

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
