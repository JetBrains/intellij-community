/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.makeFunctionTopLevel;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.refactoring.PyBaseRefactoringAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyMakeFunctionTopLevelRefactoring extends PyBaseRefactoringAction {
  public static final String ID = "py.make.function.top.level";

  @Override
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  protected boolean isEnabledOnElementInsideEditor(@NotNull PsiElement element,
                                                   @NotNull Editor editor,
                                                   @NotNull PsiFile file,
                                                   @NotNull DataContext context) {
    return findTargetFunction(element) != null;
  }

  @Override
  protected boolean isEnabledOnElementsOutsideEditor(@NotNull PsiElement[] elements) {
    return false;
  }

  @Nullable
  private static PyFunction findTargetFunction(@NotNull PsiElement element) {
    if (isLocalFunction(element) || isInstanceMethod(element)) {
      return (PyFunction)element;
    }
    final PyReferenceExpression refExpr = PsiTreeUtil.getParentOfType(element, PyReferenceExpression.class);
    if (refExpr == null) {
      return null;
    }
    final PsiElement resolved = refExpr.getReference().resolve();
    if (isLocalFunction(resolved) || isInstanceMethod(resolved)) {
      return (PyFunction)resolved;
    }
    return null;
  }

  private static boolean isInstanceMethod(@Nullable PsiElement element) {
    final PyFunction function = as(element, PyFunction.class);
    if (function == null) {
      return false;
    }
    final PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(function);
    return flags != null && flags.isInstanceMethod();
  }

  private static boolean isLocalFunction(@Nullable PsiElement resolved) {
    return resolved instanceof PyFunction && PsiTreeUtil.getParentOfType(resolved, ScopeOwner.class, true) instanceof PyFunction;
  }

  @Nullable
  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new RefactoringActionHandler() {
      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
        if (element != null) {
          final PyFunction function = findTargetFunction(element);
          if (function != null) {
            final PyBaseMakeFunctionTopLevelProcessor processor;
            if (isInstanceMethod(function)) {
              processor = new PyMakeMethodTopLevelProcessor(function, editor);
            }
            else {
              processor = new PyMakeLocalFunctionTopLevelProcessor(function, editor);
            }
            try {
              processor.run();
            }
            catch (IncorrectOperationException e) {
              if (ApplicationManager.getApplication().isUnitTestMode()) {
                throw e;
              }
              CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), e.getMessage(), ID, project);
            }
          }
        }
      }

      @Override
      public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
        // should be called only from the editor
      }
    };
  }
}
