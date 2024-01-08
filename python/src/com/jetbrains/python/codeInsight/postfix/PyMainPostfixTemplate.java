// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.refactoring.surround.surrounders.statements.PyStatementSurrounder;
import org.jetbrains.annotations.NotNull;

public class PyMainPostfixTemplate extends SurroundPostfixTemplateBase implements DumbAware {

  public static final @NlsSafe String DESCR = "if __name__ == '__main__': expr";

  protected PyMainPostfixTemplate(PostfixTemplateProvider provider) {
    super("main", DESCR, PyPostfixUtils.PY_PSI_INFO, PyPostfixUtils.currentStatementSelector(), provider);
  }

  @NotNull
  @Override
  protected Surrounder getSurrounder() {
    return new PyStatementSurrounder() {
      @NotNull
      @Override
      protected TextRange surroundStatement(@NotNull Project project, @NotNull Editor editor, PsiElement @NotNull [] elements)
        throws IncorrectOperationException {
        PyIfStatement ifStatement = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.forElement(elements[0]), PyIfStatement.class, "if __name__ == '__main__':\n expr");
        ifStatement = (PyIfStatement)CodeStyleManager.getInstance(project).reformat(ifStatement);
        final PsiElement parent = elements[0].getParent();
        ifStatement = (PyIfStatement) parent.addBefore(ifStatement, elements[0]);
        final PyStatementList statementList = ifStatement.getIfPart().getStatementList();
        statementList.addRange(elements[0], elements[elements.length - 1]);
        statementList.getFirstChild().delete();
        parent.deleteChildRange(elements[0], elements[elements.length - 1]);
        return TextRange.from(statementList.getTextRange().getEndOffset(), 0);
      }

      @Override
      public String getTemplateDescription() {
        return DESCR;
      }
    };
  }
}
