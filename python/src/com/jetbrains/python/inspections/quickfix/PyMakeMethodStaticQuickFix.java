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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: ktisha
 */
public class PyMakeMethodStaticQuickFix implements LocalQuickFix {
  public PyMakeMethodStaticQuickFix() {
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.make.static");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    final List<UsageInfo> usages = PyRefactoringUtil.findUsages(problemFunction, false);

    final PyParameter[] parameters = problemFunction.getParameterList().getParameters();
    if (parameters.length > 0) {
      parameters[0].delete();
    }
    final PyDecoratorList problemDecoratorList = problemFunction.getDecoratorList();
    List<String> decoTexts = new ArrayList<>();
    decoTexts.add("@staticmethod");
    if (problemDecoratorList != null) {
      final PyDecorator[] decorators = problemDecoratorList.getDecorators();
      for (PyDecorator deco : decorators) {
        decoTexts.add(deco.getText());
      }
    }

    PyElementGenerator generator = PyElementGenerator.getInstance(project);
    final PyDecoratorList decoratorList = generator.createDecoratorList(decoTexts.toArray(new String[decoTexts.size()]));

    if (problemDecoratorList != null) {
      problemDecoratorList.replace(decoratorList);
    }
    else {
      problemFunction.addBefore(decoratorList, problemFunction.getFirstChild());
    }

    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement instanceof PyReferenceExpression) {
        updateUsage((PyReferenceExpression)usageElement);
      }
    }
  }

  private static void updateUsage(@NotNull final PyReferenceExpression element) {
    final PyExpression qualifier = element.getQualifier();
    if (qualifier == null) return;
    final PsiReference reference = qualifier.getReference();
    if (reference == null) return;
    final PsiElement resolved = reference.resolve();
    if (resolved instanceof PyClass) {     //call with first instance argument A.m(A())
      updateArgumentList(element);
    }
  }

  private static void updateArgumentList(@NotNull final PyReferenceExpression element) {
    final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    if (callExpression == null) return;
    final PyArgumentList argumentList = callExpression.getArgumentList();
    if (argumentList == null) return;
    final PyExpression[] arguments = argumentList.getArguments();
    if (arguments.length > 0) {
      arguments[0].delete();
    }
  }
}
