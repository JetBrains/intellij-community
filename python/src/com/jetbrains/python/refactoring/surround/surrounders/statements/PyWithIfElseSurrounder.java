// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyWithIfElseSurrounder extends PyStatementSurrounder {
  @Override
  @Nullable
  protected TextRange surroundStatement(@NotNull Project project, @NotNull Editor editor, PsiElement @NotNull [] elements)
    throws IncorrectOperationException {
    PyIfStatement ifStatement =
      PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyIfStatement.class, "if True:\n    pass\nelse:    pass\n");
    final PsiElement parent = elements[0].getParent();
    final PyStatementList statementList = ifStatement.getIfPart().getStatementList();
    statementList.addRange(elements[0], elements[elements.length - 1]);
    statementList.deleteChildRange(statementList.getFirstChild(), statementList.getFirstChild());

    ifStatement = (PyIfStatement) parent.addBefore(ifStatement, elements[0]);
    parent.deleteChildRange(elements[0], elements[elements.length - 1]);

    ifStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(ifStatement);
    if (ifStatement == null) {
      return null;
    }
    return ifStatement.getTextRange();
  }

  @Override
  public String getTemplateDescription() {
    //noinspection DialogTitleCapitalization
    return CodeInsightBundle.message("surround.with.ifelse.template");
  }
}
