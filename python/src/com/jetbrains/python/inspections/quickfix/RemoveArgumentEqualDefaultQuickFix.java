// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: catherine
 *
 * QuickFix to remove redundant argument equal default
 */
public class RemoveArgumentEqualDefaultQuickFix implements LocalQuickFix {
  Set<PyExpression> myProblemElements;
  public RemoveArgumentEqualDefaultQuickFix(Set<PyExpression> problemElements) {
    myProblemElements = problemElements;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.remove.argument.equal.default");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();

    PyArgumentList argumentList = PsiTreeUtil.getParentOfType(element, PyArgumentList.class);
    if (argumentList == null) return;
    StringBuilder newArgumentList = new StringBuilder("foo(");

    PyExpression[] arguments = argumentList.getArguments();
    List<String> newArgs = new ArrayList<>();
    for (int i = 0; i != arguments.length; ++i) {
      if (!myProblemElements.contains(arguments[i])) {
        newArgs.add(arguments[i].getText());
      }
    }

    newArgumentList.append(StringUtil.join(newArgs, ", ")).append(")");
    PyExpression expression = PyElementGenerator.getInstance(project).createFromText(
      LanguageLevel.forElement(argumentList), PyExpressionStatement.class, newArgumentList.toString()).getExpression();
    if (expression instanceof PyCallExpression)
      argumentList.replace(((PyCallExpression)expression).getArgumentList());
  }
}
