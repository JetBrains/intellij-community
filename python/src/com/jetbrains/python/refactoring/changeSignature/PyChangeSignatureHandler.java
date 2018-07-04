// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
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
    final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    if (callExpression != null) {
      final PyCallable resolved = ContainerUtil.getFirstItem(callExpression.multiResolveCalleeFunction(PyResolveContext.defaultContext()));
      if (resolved instanceof PyFunction && PyiUtil.isOverload(resolved, TypeEvalContext.codeInsightFallback(callExpression.getProject()))) {
        return PyiUtil.getImplementation((PyFunction)resolved);
      }
      return resolved;
    }
    final PyFunction parent = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (parent != null && PyiUtil.isOverload(parent, TypeEvalContext.codeInsightFallback(parent.getProject()))) {
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
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, @Nullable DataContext dataContext) {
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

    if (isNotUnderSourceRoot(project, element.getContainingFile())) {
      showCannotRefactorErrorHint(project, editor, PyBundle.message("refactoring.change.signature.error.not.under.source.root"));
      return;
    }

    final PyFunction superMethod = getSuperMethod((PyFunction)element);
    if (superMethod == null) {
      return;
    }
    if (!superMethod.equals(element)) {
      element = superMethod;
      if (isNotUnderSourceRoot(project, superMethod.getContainingFile())) {
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

  private static void showCannotRefactorErrorHint(@NotNull Project project, @Nullable Editor editor, @NotNull String details) {
    final String message = RefactoringBundle.getCannotRefactorMessage(details);
    CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, "refactoring.renameRefactorings");
  }

  public static boolean isNotUnderSourceRoot(@NotNull final Project project, @Nullable final PsiFile psiFile) {
    if (psiFile == null) {
      return true;
    }
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile != null) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      if (fileIndex.isExcluded(virtualFile) || (fileIndex.isInLibraryClasses(virtualFile) && !fileIndex.isInContent(virtualFile))) {
        return true;
      }
    }
    return false;
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
      final int choice;
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        choice = Messages.YES;
      }
      else {
        choice = Messages.showYesNoCancelDialog(function.getProject(), message, REFACTORING_NAME, Messages.getQuestionIcon());
      }
      switch (choice) {
        case Messages.YES:
          return deepestSuperMethod;
        case Messages.NO:
          return function;
        default:
          return null;
      }
    }
    return function;
  }
}

