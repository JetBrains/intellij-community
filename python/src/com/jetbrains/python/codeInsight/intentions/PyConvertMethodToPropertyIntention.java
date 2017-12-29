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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PyConvertMethodToPropertyIntention extends PyBaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.method.to.property");
  }

  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.method.to.property");
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
    if (function.getParameterList().getParameters().length > 1) return false;

    final PyDecoratorList decoratorList = function.getDecoratorList();
    if (decoratorList != null) return false;

    final boolean[] available = {false};
    function.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReturnStatement(PyReturnStatement node) {
        if (node.getExpression() != null)
          available[0] = true;
      }

      @Override
      public void visitPyYieldExpression(PyYieldExpression node) {
        available[0] = true;
      }
    });

    return available[0];
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    final PyClass containingClass = problemFunction.getContainingClass();
    if (containingClass == null) return;
    final List<UsageInfo> usages = PyRefactoringUtil.findUsages(problemFunction, false);

    if (!prepareForWrite(file, usages)) return;

    final PyDecoratorList problemDecoratorList = problemFunction.getDecoratorList();
    List<String> decoTexts = new ArrayList<>();
    decoTexts.add("@property");
    if (problemDecoratorList != null) {
      final PyDecorator[] decorators = problemDecoratorList.getDecorators();
      for (PyDecorator deco : decorators) {
        decoTexts.add(deco.getText());
      }
    }

    WriteAction.run(() -> {
      ensureDecoratorList(problemFunction, problemDecoratorList, decoTexts);
      deleteUsages(usages);
    });
  }

  private static boolean prepareForWrite(PsiFile file, List<UsageInfo> usages) {
    List<PsiElement> toWrite = ContainerUtil.newArrayList(file);
    toWrite.addAll(ContainerUtil.mapNotNull(usages, UsageInfo::getElement));
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(toWrite)) return false;
    return true;
  }

  private static void ensureDecoratorList(PyFunction problemFunction, @Nullable PyDecoratorList problemDecoratorList, List<String> decoTexts) {
    PyElementGenerator generator = PyElementGenerator.getInstance(problemFunction.getProject());
    PyDecoratorList decoratorList = generator.createDecoratorList(ArrayUtil.toStringArray(decoTexts));

    if (problemDecoratorList != null) {
      problemDecoratorList.replace(decoratorList);
    }
    else {
      problemFunction.addBefore(decoratorList, problemFunction.getFirstChild());
    }
  }

  private static void deleteUsages(List<UsageInfo> usages) {
    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement instanceof PyReferenceExpression) {
        final PsiElement parent = usageElement.getParent();
        if (parent instanceof PyCallExpression) {
          final PyArgumentList argumentList = ((PyCallExpression)parent).getArgumentList();
          if (argumentList != null) argumentList.delete();
        }
      }
    }
  }
}
