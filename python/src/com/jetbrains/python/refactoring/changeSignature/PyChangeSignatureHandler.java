// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */

public class PyChangeSignatureHandler implements ChangeSignatureHandler {
  @Nullable
  @Override
  public PsiElement findTargetMember(@NotNull PsiFile file, @NotNull Editor editor) {
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    return findTargetMember(element);
  }

  @Nullable
  @Override
  public PsiElement findTargetMember(@Nullable PsiElement element) {
    if (element == null) return null;
    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(element.getProject());

    final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    if (callExpression != null) {
      final PyResolveContext resolveContext = PyResolveContext.implicitContext(context);
      final PyCallable resolved = ContainerUtil.getFirstItem(callExpression.multiResolveCalleeFunction(resolveContext));

      return resolved instanceof PyFunction && PyiUtil.isOverload(resolved, context)
             ? PyiUtil.getImplementation((PyFunction)resolved, context)
             : resolved;
    }

    final PyFunction parent = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (parent != null && PyiUtil.isOverload(parent, context)) {
      return null;
    }

    return parent;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = findTargetMember(file, editor);
    if (element == null) {
      element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    invokeOnElement(project, element, editor);
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, @Nullable DataContext dataContext) {
    if (elements.length != 1) {
      return;
    }
    final Editor editor = dataContext == null ? null : CommonDataKeys.EDITOR.getData(dataContext);
    invokeOnElement(project, elements[0], editor);
  }

  @Nullable
  @Override
  public String getTargetNotFoundMessage() {
    return PyBundle.message("refactoring.change.signature.error.wrong.caret.position.method.name");
  }

  private static void invokeOnElement(Project project, PsiElement element, Editor editor) {
    if (element instanceof PyLambdaExpression) {
      showCannotRefactorErrorHint(project, editor, PyBundle.message("refactoring.change.signature.error.lambda.call"));
      return;
    }
    if (!(element instanceof PyFunction)) {
      showCannotRefactorErrorHint(project, editor, PyBundle.message("refactoring.change.signature.error.wrong.caret.position.method.name"));
      return;
    }

    if (PyPsiIndexUtil.isNotUnderSourceRoot(project, element.getContainingFile())) {
      showCannotRefactorErrorHint(project, editor, PyBundle.message("refactoring.change.signature.error.not.under.source.root"));
      return;
    }

    final PyFunction superMethod = getSuperMethod((PyFunction)element);
    if (superMethod == null) {
      return;
    }
    if (!superMethod.equals(element)) {
      element = superMethod;
      if (PyPsiIndexUtil.isNotUnderSourceRoot(project, superMethod.getContainingFile())) {
        return;
      }
    }

    final PyFunction function = (PyFunction)element;
    final PyParameter[] parameters = function.getParameterList().getParameters();
    for (PyParameter p : parameters) {
      if (p instanceof PyTupleParameter) {
        showCannotRefactorErrorHint(project, editor, PyBundle.message("refactoring.change.signature.error.tuple.parameters"));
        return;
      }
    }

    final PyMethodDescriptor method = new PyMethodDescriptor((PyFunction)element);
    final PyChangeSignatureDialog dialog = new PyChangeSignatureDialog(project, method);
    dialog.show();
  }

  private static void showCannotRefactorErrorHint(@NotNull Project project, @Nullable Editor editor, @NotNull @NlsContexts.DialogMessage String details) {
    final String message = RefactoringBundle.getCannotRefactorMessage(details);
    CommonRefactoringUtil.showErrorHint(project, editor, message, RefactoringBundle.message("changeSignature.refactoring.name"), "refactoring.renameRefactorings");
  }

  @Nullable
  protected static PyFunction getSuperMethod(@Nullable PyFunction function) {
    if (function == null) {
      return null;
    }
    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null) {
      return function;
    }
    if (PyUtil.isInitOrNewMethod(function)) {
      return function;
    }
    final PyFunction deepestSuperMethod = PySuperMethodsSearch.findDeepestSuperMethod(function);
    if (!deepestSuperMethod.equals(function)) {
      final PyClass baseClass = deepestSuperMethod.getContainingClass();
      final PyBuiltinCache cache = PyBuiltinCache.getInstance(baseClass);
      final String baseClassName = baseClass == null ? "" : baseClass.getName();
      if (cache.isBuiltin(baseClass)) {
        return function;
      }
      final String message = PyBundle.message("refactoring.change.signature.find.usages.of.base.class",
                                              function.getName(),
                                              containingClass.getName(),
                                              baseClassName);
      final int choice = Messages.showYesNoCancelDialog(function.getProject(), message, 
                                                        RefactoringBundle.message("changeSignature.refactoring.name"), Messages.getQuestionIcon());
      return switch (choice) {
        case Messages.YES -> deepestSuperMethod;
        case Messages.NO -> function;
        default -> null;
      };
    }
    return function;
  }
}

