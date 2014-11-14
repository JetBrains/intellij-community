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

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 28, 2009
 * Time: 6:00:47 PM
 */
public class PyWithReturnSurrounder extends PyStatementSurrounder {
  public boolean isApplicable(@NotNull PsiElement[] elements) {
    return (elements.length == 1) &&
           (elements[0] instanceof PyExpressionStatement) &&
           (PsiTreeUtil.getParentOfType(elements[0], PyFunction.class) != null);
  }

  @Override
  @Nullable
  protected TextRange surroundStatement(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements)
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

  public String getTemplateDescription() {
    return PyBundle.message("surround.with.return.template");
  }
}
