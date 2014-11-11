/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.surround.surrounders.statements;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 27, 2009
 * Time: 8:49:16 PM
 */
public class PyWithWhileSurrounder extends PyStatementSurrounder{
  @Override
  @Nullable
  protected TextRange surroundStatement(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements)
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

  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.while.template");
  }
}
