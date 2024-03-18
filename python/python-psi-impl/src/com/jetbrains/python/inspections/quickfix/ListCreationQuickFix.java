// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.PyListCreationInspection;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * User : catherine
 */
public class ListCreationQuickFix extends PsiUpdateModCommandQuickFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.list.creation");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    final PyAssignmentStatement assignmentStatement = as(element, PyAssignmentStatement.class);
    if (assignmentStatement == null) return;
    final PyExpression assignedValue = assignmentStatement.getAssignedValue();
    if (assignedValue == null) return;

    List<PyExpressionStatement> appendCalls = PyListCreationInspection.collectSubsequentListAppendCalls(assignmentStatement);
    final List<PyExpression> items = buildLiteralItems(assignedValue, appendCalls);
    appendCalls.forEach(PyExpressionStatement::delete);

    final String text = "[" + StringUtil.join(items, PyExpression::getText, ", ") + "]";
    assignedValue.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(assignedValue), text));
  }

  @NotNull
  private static List<PyExpression> buildLiteralItems(@NotNull PyExpression assignedValue, List<PyExpressionStatement> statements) {
    final List<PyExpression> values = new ArrayList<>();

    ContainerUtil.addAll(values, ((PyListLiteralExpression)assignedValue).getElements());

    for (PyExpressionStatement statement : statements) {
      ContainerUtil.addAll(values, ((PyCallExpression)statement.getExpression()).getArguments());
    }

    return values;
  }
}
