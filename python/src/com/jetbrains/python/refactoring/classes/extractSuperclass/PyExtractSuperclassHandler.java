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
package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.PsiNavigateUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.refactoring.classes.PyClassMembersRefactoringSupport;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringHandler;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;

import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
public class PyExtractSuperclassHandler extends PyClassRefactoringHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("extract.superclass.title");

  @Override
  protected void doRefactor(Project project, PsiElement element1, PsiElement element2, Editor editor, PsiFile file, DataContext dataContext) {
    CommonRefactoringUtil.checkReadOnlyStatus(project, file);

    final PyClass clazz = PyUtil.getContainingClassOrSelf(element1);
    if (!inClass(clazz, project, editor, "refactoring.pull.up.error.cannot.perform.refactoring.not.inside.class")) return;

    final PyMemberInfoStorage infoStorage = PyClassMembersRefactoringSupport.getSelectedMemberInfos(clazz, element1, element2);

    if (ApplicationManagerEx.getApplicationEx().isUnitTestMode()) return;

    final PyExtractSuperclassDialog dialog = new PyExtractSuperclassDialog(project, clazz, infoStorage);
    dialog.show();
    if(dialog.isOK()) {
      extractWithHelper(clazz, dialog.getSelectedMemberInfos(), dialog.getSuperBaseName(), dialog.getTargetFile());
    }
  }

  private static void extractWithHelper(PyClass clazz, Collection<PyMemberInfo> selectedMemberInfos, String superBaseName, String targetFile) {
    PsiNavigateUtil.navigate(PyExtractSuperclassHelper.extractSuperclass(clazz, selectedMemberInfos, superBaseName, targetFile));
  }

  @Override
  protected String getTitle() {
    return REFACTORING_NAME;
  }

  @Override
  protected String getHelpId() {
    return "refactoring.extractSuperclass";
  }
}
