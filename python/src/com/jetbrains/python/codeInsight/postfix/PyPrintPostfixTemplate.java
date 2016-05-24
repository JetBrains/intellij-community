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
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.surround.surrounders.expressions.PyExpressionSurrounder;
import org.jetbrains.annotations.NotNull;

public class PyPrintPostfixTemplate extends SurroundPostfixTemplateBase {

  public static final String DESCR = "print(expr)";

  protected PyPrintPostfixTemplate() {
    super("print", DESCR, PyPostfixUtils.PY_PSI_INFO, PyPostfixUtils.selectorTopmost());
  }

  @NotNull
  @Override
  protected Surrounder getSurrounder() {
    return new PyExpressionSurrounder() {
      @Override
      public boolean isApplicable(@NotNull PyExpression expr) {
        return true;
      }

      @Override
      public TextRange surroundExpression(@NotNull Project project, @NotNull Editor editor, @NotNull PyExpression expression)
        throws IncorrectOperationException {
        LanguageLevel level = LanguageLevel.forElement(expression);
        String textToGenerate = level.isPy3K() ? "print(a)" : "print a";
        PyStatement pyStatement = PyElementGenerator.getInstance(project).createFromText(level, PyStatement.class, textToGenerate);
        if (!level.isPy3K()) {
          pyStatement.getLastChild().replace(expression);
        } else {
          PyArgumentList argumentList = PsiTreeUtil.findChildOfType(pyStatement, PyArgumentList.class);
          if (argumentList == null) {
            return null;
          }
          argumentList.getArguments()[0].replace(expression);
        }
        pyStatement = (PyStatement)CodeStyleManager.getInstance(project).reformat(pyStatement);
        pyStatement = (PyStatement)expression.getParent().replace(pyStatement);
        return TextRange.from(pyStatement.getTextRange().getEndOffset(), 0);
      }

      @Override
      public String getTemplateDescription() {
        return DESCR;
      }
    };
  }
}
