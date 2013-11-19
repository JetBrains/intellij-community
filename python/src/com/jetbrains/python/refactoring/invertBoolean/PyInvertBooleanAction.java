/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.invertBoolean;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class PyInvertBooleanAction extends BaseRefactoringAction {
  @Override
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  protected boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    if (elements.length == 1) {
      return isApplicable(elements[0], elements[0].getContainingFile());
    }
    return false;
  }

  private static boolean isApplicable(@NotNull final PsiElement element, @NotNull final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && ProjectRootManager.getInstance(element.getProject()).getFileIndex().isInLibraryClasses(virtualFile)) return false;
    if (element instanceof PyTargetExpression) {
      final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
      if (assignmentStatement != null) {
        final PyExpression assignedValue = assignmentStatement.getAssignedValue();
        if (assignedValue == null) return false;
        final String name = assignedValue.getText();
        return name != null && (PyNames.TRUE.equals(name) || PyNames.FALSE.equals(name));
      }
    }
    if (element instanceof PyNamedParameter) {
      final PyExpression defaultValue = ((PyNamedParameter)element).getDefaultValue();
      if (defaultValue instanceof PyBoolLiteralExpression) return true;
    }
    return element.getParent() instanceof PyBoolLiteralExpression;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull final PsiElement element, @NotNull final Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    return isApplicable(element, element.getContainingFile());
  }

  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new PyInvertBooleanHandler();
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return language.isKindOf(PythonLanguage.getInstance());
  }
}
