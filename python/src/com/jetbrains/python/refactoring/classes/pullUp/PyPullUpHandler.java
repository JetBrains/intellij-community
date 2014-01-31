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
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.refactoring.classes.PyClassMembersRefactoringSupport;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringHandler;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.vp.Creator;
import com.jetbrains.python.vp.ViewPresenterUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author: Dennis.Ushakov
 */
public class PyPullUpHandler extends PyClassRefactoringHandler {
  public static final String REFACTORING_NAME = PyBundle.message("refactoring.pull.up.dialog.title");

  @Override
  protected void doRefactor(final Project project,
                            PsiElement element1,
                            PsiElement element2,
                            Editor editor,
                            PsiFile file,
                            DataContext dataContext) {
    //TODO: Move to vp (presenter) as well
    CommonRefactoringUtil.checkReadOnlyStatus(project, file);

    final PyClass clazz = PyUtil.getContainingClassOrSelf(element1);
    if (!inClass(clazz, project, editor, "refactoring.pull.up.error.cannot.perform.refactoring.not.inside.class")) return;
    assert clazz != null;

    final PyMemberInfoStorage infoStorage = PyClassMembersRefactoringSupport.getSelectedMemberInfos(clazz, element1, element2);
    if (PyAncestorsUtils.getAncestorsUnderUserControl(clazz).isEmpty() || infoStorage.getClassMemberInfos(clazz).isEmpty()) {
      CommonRefactoringUtil.showErrorHint(project, editor, PyBundle
        .message("refactoring.pull.up.error.cannot.perform.refactoring.no.base.classes", clazz.getName()),
                                          RefactoringBundle.message("pull.members.up.title"), "members.pull.up");
      return;
    }

    if (ApplicationManagerEx.getApplicationEx().isUnitTestMode()) return;

    ViewPresenterUtils.linkViewWithPresenterAndLaunch(PyPullUpPresenter.class, PyPullUpView.class,
                                                      new Creator<PyPullUpView, PyPullUpPresenter>() {
                                                        @NotNull
                                                        @Override
                                                        public PyPullUpPresenter createPresenter(@NotNull PyPullUpView view) {
                                                          return new PyPullUpPresenterImpl(view, infoStorage, clazz);
                                                        }

                                                        @NotNull
                                                        @Override
                                                        public PyPullUpView createView(@NotNull PyPullUpPresenter presenter) {
                                                          return new PullUpViewSwingImpl(project, presenter, clazz);
                                                        }
                                                      });

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
