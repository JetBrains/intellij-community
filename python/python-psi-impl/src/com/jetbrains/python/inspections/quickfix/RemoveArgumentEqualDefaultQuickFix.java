// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: catherine
 * <p>
 * QuickFix to remove redundant argument equal default
 */
public class RemoveArgumentEqualDefaultQuickFix implements LocalQuickFix {
  private final List<SmartPsiElementPointer<PyExpression>> myProblemElements;

  public RemoveArgumentEqualDefaultQuickFix(@NotNull Collection<PyExpression> problemElements) {
    myProblemElements = new ArrayList<>();
    for (PyExpression element : problemElements) {
      myProblemElements.add(SmartPointerManager.createPointer(element));
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.remove.argument.equal.default");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    List<PyExpression> problemElements = ContainerUtil.map(myProblemElements, SmartPsiElementPointer::getElement);
    if (problemElements.contains(null)) return;

    PyArgumentList argumentList = PsiTreeUtil.getParentOfType(element, PyArgumentList.class);
    if (argumentList == null) return;
    StringBuilder newArgumentList = new StringBuilder("foo(");

    PyExpression[] arguments = argumentList.getArguments();
    List<String> newArgs = new ArrayList<>();
    for (int i = 0; i != arguments.length; ++i) {
      if (!problemElements.contains(arguments[i])) {
        newArgs.add(arguments[i].getText());
      }
    }

    newArgumentList.append(StringUtil.join(newArgs, ", ")).append(")");
    PyExpression expression = PyElementGenerator.getInstance(project).createFromText(
      LanguageLevel.forElement(argumentList), PyExpressionStatement.class, newArgumentList.toString()).getExpression();
    if (expression instanceof PyCallExpression) {
      argumentList.replace(((PyCallExpression)expression).getArgumentList());
    }
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    List<PyExpression> copies = new ArrayList<>();
    for (SmartPsiElementPointer<PyExpression> element : myProblemElements) {
      PyExpression copy = PyRefactoringUtil.findSameElementForPreview(element, target);
      if (copy == null) return null;
      copies.add(copy);
    }
    return new RemoveArgumentEqualDefaultQuickFix(copies);
  }
}
