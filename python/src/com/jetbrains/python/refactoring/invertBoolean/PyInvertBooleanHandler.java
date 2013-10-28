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

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyNamedParameter;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class PyInvertBooleanHandler implements RefactoringActionHandler {
  static final String REFACTORING_NAME = RefactoringBundle.message("invert.boolean.title");

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element == null && editor != null && file != null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }
    final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
    if (assignmentStatement != null) {
      invoke(assignmentStatement.getTargets()[0]);
    }
    else if (element instanceof PyNamedParameter) {
      invoke(element);
    }
    else {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(
          RefactoringBundle.message("error.wrong.caret.position.local.or.expression.name")), REFACTORING_NAME, "refactoring.invertBoolean");
    }
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (elements.length == 1) {
      final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(elements[0], PyAssignmentStatement.class);
      if (assignmentStatement != null) {
        invoke(assignmentStatement.getTargets()[0]);
      }
    }
  }

  private static void invoke(@NotNull final PsiElement element) {
    new PyInvertBooleanDialog(element).show();
  }
}
