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
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
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
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */

public class PyChangeSignatureHandler implements ChangeSignatureHandler {
  @Nullable
  @Override
  public PsiElement findTargetMember(PsiFile file, Editor editor) {
    PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    return findTargetMember(element);
  }

  @Nullable
  @Override
  public PsiElement findTargetMember(@Nullable PsiElement element) {
    final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    if (callExpression != null) {
      return callExpression.resolveCalleeFunction(PyResolveContext.defaultContext());
    }
    return PsiTreeUtil.getParentOfType(element, PyFunction.class);
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
    if (elements.length != 1) return;
    Editor editor = dataContext == null ? null : CommonDataKeys.EDITOR.getData(dataContext);
    invokeOnElement(project, elements[0], editor);
  }

  @Nullable
  @Override
  public String getTargetNotFoundMessage() {
    return PyBundle.message("refactoring.change.signature.error.wrong.caret.position.method.name");
  }

  private static void invokeOnElement(Project project, PsiElement element, Editor editor) {
    if (element instanceof PyLambdaExpression) {
      String message =
        RefactoringBundle.getCannotRefactorMessage("Caret is positioned on lambda call.");
      CommonRefactoringUtil.showErrorHint(project, editor, message,
                                          REFACTORING_NAME, REFACTORING_NAME);
      return;
    }
    if (!(element instanceof PyFunction)) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(
          PyBundle.message("refactoring.change.signature.error.wrong.caret.position.method.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message,
                                          REFACTORING_NAME, REFACTORING_NAME);
      return;
    }

    if (isNotUnderSourceRoot(project, element.getContainingFile(), editor)) return;

    final PyFunction superMethod = getSuperMethod((PyFunction)element);
    if (superMethod == null) return;
    if (!superMethod.equals(element)) {
      element = superMethod;
      if (isNotUnderSourceRoot(project, superMethod.getContainingFile(), editor)) return;
    }

    final PyFunction function = (PyFunction)element;
    final PyParameter[] parameters = function.getParameterList().getParameters();
    for (PyParameter p : parameters) {
      if (p instanceof PyTupleParameter) {
        String message =
          RefactoringBundle.getCannotRefactorMessage("Function contains tuple parameters");
        CommonRefactoringUtil.showErrorHint(project, editor, message,
                                            REFACTORING_NAME, REFACTORING_NAME);
        return;
      }
    }

    final PyMethodDescriptor method = new PyMethodDescriptor((PyFunction)element);
    PyChangeSignatureDialog dialog = new PyChangeSignatureDialog(project, method);
    dialog.show();
  }

  private static boolean isNotUnderSourceRoot(@NotNull final Project project,
                                              @Nullable final PsiFile psiFile,
                                              @Nullable final Editor editor) {
    if (psiFile == null) return true;
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile != null) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      if (fileIndex.isExcluded(virtualFile) || (fileIndex.isInLibraryClasses(virtualFile) && !fileIndex.isInContent(virtualFile))) {
        String message = RefactoringBundle.getCannotRefactorMessage("Function is not under the source root");
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, REFACTORING_NAME);
        return true;
      }
    }
    return false;
  }

  @Nullable
  protected static PyFunction getSuperMethod(@Nullable PyFunction function) {
    if (function == null) return null;
    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null) {
      return function;
    }
    final PyFunction deepestSuperMethod = PySuperMethodsSearch.findDeepestSuperMethod(function);
    if (!deepestSuperMethod.equals(function)) {
      final PyClass baseClass = deepestSuperMethod.getContainingClass();
      final PyBuiltinCache cache = PyBuiltinCache.getInstance(baseClass);
      String baseClassName = baseClass == null? "" : baseClass.getName();
      if (cache.isBuiltin(baseClass))
        return function;
      final String message = PyBundle.message(
        "refactoring.change.signature.find.usages.of.base.class",
        function.getName(),
        containingClass.getName(),
        baseClassName);
      int choice;
      if (ApplicationManagerEx.getApplicationEx().isUnitTestMode()) {
        choice = 0;
      }
      else {
        choice = Messages.showYesNoCancelDialog(function.getProject(), message,
                                                REFACTORING_NAME, Messages.getQuestionIcon());
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

