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

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.remove.argument.equal.default");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

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
