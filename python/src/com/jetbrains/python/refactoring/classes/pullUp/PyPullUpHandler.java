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
package com.jetbrains.python.refactoring.classes.pullUp;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.PsiNavigateUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.refactoring.classes.PyClassMembersRefactoringSupport;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringHandler;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;

import java.util.Collection;

/**
 * @author: Dennis.Ushakov
 */
public class PyPullUpHandler extends PyClassRefactoringHandler {
  public static final String REFACTORING_NAME = PyBundle.message("refactoring.pull.up.dialog.title");

  @Override
  protected void doRefactor(Project project, PsiElement element1, PsiElement element2, Editor editor, PsiFile file, DataContext dataContext) {
    CommonRefactoringUtil.checkReadOnlyStatus(project, file);

    final PyClass clazz = PyUtil.getContainingClassOrSelf(element1);
    if (!inClass(clazz, project, editor, "refactoring.pull.up.error.cannot.perform.refactoring.not.inside.class")) return;

    final PyMemberInfoStorage infoStorage = PyClassMembersRefactoringSupport.getSelectedMemberInfos(clazz, element1, element2);
    final Collection<PyClass> classes = infoStorage.getClasses();
    if (classes.size() == 0) {
      assert clazz != null;
      CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.pull.up.error.cannot.perform.refactoring.no.base.classes", clazz.getName()),
                                          RefactoringBundle.message("pull.members.up.title"),
                                          "members.pull.up");
      return;
    }

    if (ApplicationManagerEx.getApplicationEx().isUnitTestMode()) return;
       
    final PyPullUpDialog dialog = new PyPullUpDialog(project, clazz, classes, infoStorage);
    dialog.show();
    if(dialog.isOK()) {
      pullUpWithHelper(clazz, dialog.getSelectedMemberInfos(), dialog.getSuperClass());
    }
  }

  private static void pullUpWithHelper(PyClass clazz, Collection<PyMemberInfo> selectedMemberInfos, PyClass superClass) {
    PsiNavigateUtil.navigate(PyPullUpHelper.pullUp(clazz, selectedMemberInfos, superClass));
  }

  @Override
  protected String getTitle() {
    return REFACTORING_NAME;
  }

  @Override
  protected String getHelpId() {
    return "refactoring.pullMembersUp";
  }
}
