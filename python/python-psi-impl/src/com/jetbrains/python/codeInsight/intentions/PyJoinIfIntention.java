// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * User: catherine
 * Intention to merge the if clauses in the case of nested ifs where only the inner if contains code (the outer if only contains the inner one)
 * For instance,
 * if a:
 *   if b:
 *    # stuff here
 * into
 * if a and b:
 *   #stuff here
 */
public final class PyJoinIfIntention extends PsiUpdateModCommandAction<PyIfStatement> {
  public PyJoinIfIntention() {
    super(PyIfStatement.class);
  }
  
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.join.if");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PyIfStatement expression) {
    PyIfStatement outer = getIfStatement(expression);
    if (outer == null) return null;
    if (outer.getElsePart() != null || outer.getElifParts().length > 0) return null;
    PyStatement firstStatement = getFirstStatement(outer);
    PyStatementList outerStList = outer.getIfPart().getStatementList();
    if (outerStList.getStatements().length != 1) return null;
    if (!(firstStatement instanceof PyIfStatement inner)) return null;
    if (inner.getElsePart() != null || inner.getElifParts().length > 0) return null;
    PyStatementList stList = inner.getIfPart().getStatementList();
    if (stList.getStatements().length == 0) return null;
    return Presentation.of(PyPsiBundle.message("INTN.join.if"));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PyIfStatement expression, @NotNull ModPsiUpdater updater) {
    PyIfStatement outerIfStatement = getIfStatement(expression);
    if (outerIfStatement == null) return;

    PyIfStatement innerIfStatement = as(getFirstStatement(outerIfStatement), PyIfStatement.class);
    if (innerIfStatement == null) return;

    PyExpression innerCondition = innerIfStatement.getIfPart().getCondition();
    PyExpression outerCondition = outerIfStatement.getIfPart().getCondition();
    if (outerCondition == null || innerCondition == null) return;

    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(context.project());
    LanguageLevel pyVersion = LanguageLevel.forElement(context.file());
    PyBinaryExpression fakeAndExpression = (PyBinaryExpression)elementGenerator.createExpressionFromText(pyVersion, "foo and bar");
    StringBuilder replacementText = new StringBuilder();
    if (PyReplaceExpressionUtil.isNeedParenthesis(fakeAndExpression.getLeftExpression(), outerCondition)) {
      replacementText.append("(").append(outerCondition.getText()).append(")");
    }
    else {
      replacementText.append(outerCondition.getText());
    }
    replacementText.append(" and ");
    if (PyReplaceExpressionUtil.isNeedParenthesis(fakeAndExpression.getLeftExpression(), innerCondition)) {
      replacementText.append("(").append(innerCondition.getText()).append(")");
    }
    else {
      replacementText.append(innerCondition.getText());
    }

    PyExpression newCondition = elementGenerator.createExpressionFromText(pyVersion, replacementText.toString());
    outerCondition.replace(newCondition);

    PyStatementList innerStatementList = innerIfStatement.getIfPart().getStatementList();
    PyStatementList outerStatementList = outerIfStatement.getIfPart().getStatementList();
    List<PsiComment> comments = PsiTreeUtil.getChildrenOfTypeAsList(outerIfStatement.getIfPart(), PsiComment.class);
    comments = ContainerUtil.concat(comments, PsiTreeUtil.getChildrenOfTypeAsList(innerIfStatement.getIfPart(), PsiComment.class));
    comments = ContainerUtil.concat(comments, PsiTreeUtil.getChildrenOfTypeAsList(outerStatementList, PsiComment.class));
    comments = ContainerUtil.concat(comments, PsiTreeUtil.getChildrenOfTypeAsList(innerStatementList, PsiComment.class));

    for (PsiElement comm : comments) {
      outerIfStatement.getIfPart().addBefore(comm, outerStatementList);
      comm.delete();
    }
    outerStatementList.replace(innerStatementList);
  }

  @Nullable
  private static PyStatement getFirstStatement(@NotNull PyIfStatement ifStatement) {
    PyStatementList stList = ifStatement.getIfPart().getStatementList();
    return ArrayUtil.getFirstElement(stList.getStatements());
  }

  @Nullable
  private static PyIfStatement getIfStatement(@Nullable PyIfStatement ifStatement) {
    while (ifStatement != null && !(getFirstStatement(ifStatement) instanceof PyIfStatement)) {
      ifStatement = PsiTreeUtil.getParentOfType(ifStatement, PyIfStatement.class);
    }
    return ifStatement;
  }
}
