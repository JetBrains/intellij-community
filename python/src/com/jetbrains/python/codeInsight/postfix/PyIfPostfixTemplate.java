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
package com.jetbrains.python.codeInsight.postfix;

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.surround.surrounders.statements.PyStatementSurrounder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyIfPostfixTemplate extends SurroundPostfixTemplateBase {

  public static final String TEMPLATE_DESCRIPTION = "if expr";

  public PyIfPostfixTemplate() {
    super("if", TEMPLATE_DESCRIPTION, PyPostfixUtils.PY_PSI_INFO, PyPostfixUtils.selectorTopmost());
  }

  @NotNull
  @Override
  protected Surrounder getSurrounder() {
    return new PyIfSurrounder();
  }

  private static class PyIfSurrounder extends PyStatementSurrounder {

    @Nullable
    @Override
    protected TextRange surroundStatement(@NotNull Project project, @NotNull final Editor editor, @NotNull PsiElement[] elements)
      throws IncorrectOperationException {
      String text = "if a:\n pass";
      PyIfStatement ifStatement = PyElementGenerator.getInstance(project).
        createFromText(LanguageLevel.getDefault(), PyIfStatement.class, text);
      final PsiElement element = elements[0];
      final PyExpression condition = ifStatement.getIfPart().getCondition();
      if (condition != null) {
        condition.replace(element);
      }
      ifStatement = (PyIfStatement)CodeStyleManager.getInstance(project).reformat(ifStatement);
      ifStatement = (PyIfStatement)element.getParent().replace(ifStatement);
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
      PyStatementList statementList = ifStatement.getIfPart().getStatementList();
      PyStatement[] statements = statementList.getStatements();
      final TextRange range = statements[0].getTextRange();
      editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
      return TextRange.from(range.getStartOffset(), 0);
    }

    @Override
    public String getTemplateDescription() {
      return TEMPLATE_DESCRIPTION;
    }
  }
}
