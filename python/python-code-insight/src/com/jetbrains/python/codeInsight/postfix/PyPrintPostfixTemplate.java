// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.refactoring.surround.surrounders.expressions.PyExpressionSurrounder;
import org.jetbrains.annotations.NotNull;

public class PyPrintPostfixTemplate extends SurroundPostfixTemplateBase implements DumbAware {

  public static final @NlsSafe String DESCR = "print(expr)";

  protected PyPrintPostfixTemplate(PostfixTemplateProvider provider) {
    super("print", DESCR, PyPostfixUtils.PY_PSI_INFO, PyPostfixUtils.selectorTopmost(), provider);
  }

  @Override
  protected @NotNull Surrounder getSurrounder() {
    return new PyExpressionSurrounder() {
      @Override
      public boolean isApplicable(@NotNull PyExpression expr) {
        return true;
      }

      @Override
      public TextRange surroundExpression(@NotNull Project project, @NotNull Editor editor, @NotNull PyExpression expression)
        throws IncorrectOperationException {
        LanguageLevel level = LanguageLevel.forElement(expression);
        String textToGenerate = !level.isPython2() ? "print(a)" : "print a";
        PyStatement pyStatement = PyElementGenerator.getInstance(project).createFromText(level, PyStatement.class, textToGenerate);
        if (level.isPython2()) {
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
