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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: ktisha
 */
public class PyConvertStaticMethodToFunctionIntention extends BaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.static.method.to.function");
  }

  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.static.method.to.function");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (function == null) return false;
    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null) return false;
    final PyDecoratorList decoratorList = function.getDecoratorList();
    if (decoratorList != null) {
      final PyDecorator staticMethod = decoratorList.findDecorator(PyNames.STATICMETHOD);
      if (staticMethod != null) return true;
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    final PyClass containingClass = problemFunction.getContainingClass();
    if (containingClass == null) return;
    final List<UsageInfo> usages = PyRefactoringUtil.findUsages(problemFunction, false);
    final PyDecoratorList decoratorList = problemFunction.getDecoratorList();
    if (decoratorList != null) {
      final PyDecorator decorator = decoratorList.findDecorator(PyNames.STATICMETHOD);
      if (decorator != null) decorator.delete();
    }

    final PsiElement copy = problemFunction.copy();
    problemFunction.delete();
    file.addAfter(copy, containingClass);

    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement instanceof PyReferenceExpression) {
        PyUtil.removeQualifier((PyReferenceExpression)usageElement);
      }
    }
  }
}
