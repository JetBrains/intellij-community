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
package com.jetbrains.python.refactoring.classes;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dennis.Ushakov
 */
public abstract class PyClassRefactoringHandler implements RefactoringActionHandler, ElementsHandler {
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element1 = null;
    PsiElement element2 = null;
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      element1 = file.findElementAt(selectionModel.getSelectionStart());
      element2 = file.findElementAt(selectionModel.getSelectionEnd() - 1);
    }
    else {
      final CaretModel caretModel = editor.getCaretModel();
      final Document document = editor.getDocument();
      int lineNumber = document.getLineNumber(caretModel.getOffset());
      if ((lineNumber >= 0) && (lineNumber < document.getLineCount())) {
        element1 = file.findElementAt(document.getLineStartOffset(lineNumber));
        element2 = file.findElementAt(document.getLineEndOffset(lineNumber) - 1);
      }
    }
    if (element1 == null || element2 == null) {
      CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.introduce.selection.error"), getTitle(),
                                          "members.pull.up");
      return;
    }
    doRefactor(project, element1, element2, editor, file, dataContext);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    doRefactor(project, elements[0], elements[elements.length - 1], editor, file, dataContext);
  }

  private void doRefactor(Project project, PsiElement element1, PsiElement element2, Editor editor, PsiFile file, DataContext dataContext) {
    if (ApplicationManagerEx.getApplicationEx().isUnitTestMode()) return;

    CommonRefactoringUtil.checkReadOnlyStatus(project, file);

    final PyClass clazz = PyUtil.getContainingClassOrSelf(element1);
    if (!inClass(clazz, project, editor, "refactoring.pull.up.error.cannot.perform.refactoring.not.inside.class")) return;
    assert clazz != null;

    final PyMemberInfoStorage infoStorage = PyMembersRefactoringSupport.getSelectedMemberInfos(clazz, element1, element2);

    doRefactorImpl(project, clazz, infoStorage, editor);
  }


  protected abstract void doRefactorImpl(@NotNull final Project project,
                                         @NotNull final PyClass classUnderRefactoring,
                                         @NotNull final PyMemberInfoStorage infoStorage,
                                         @NotNull final Editor editor);



  protected boolean inClass(PyClass clazz, Project project, Editor editor, String errorMessageId) {
    if (clazz == null) {
      CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message(errorMessageId), getTitle(), getHelpId());
      return false;
    }
    return true;
  }

  protected abstract String getTitle();
  protected abstract String getHelpId();

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && elements[0] instanceof PyClass;
  }
}
