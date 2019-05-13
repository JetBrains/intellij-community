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
package com.jetbrains.python.refactoring.classes.pullUp;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
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
  protected void doRefactorImpl(@NotNull final Project project,
                                @NotNull final PyClass classUnderRefactoring,
                                @NotNull final PyMemberInfoStorage infoStorage,
                                @NotNull final Editor editor) {
    //TODO: Move to vp (presenter) as well
    final PyPullUpNothingToRefactorMessage nothingToRefactor = new PyPullUpNothingToRefactorMessage(project, editor, classUnderRefactoring);

    if (PyAncestorsUtils.getAncestorsUnderUserControl(classUnderRefactoring).isEmpty()) {
      nothingToRefactor.showNothingToRefactor();
      return;
    }


    ViewPresenterUtils
      .linkViewWithPresenterAndLaunch(PyPullUpPresenter.class, PyPullUpView.class, new Creator<PyPullUpView, PyPullUpPresenter>() {
                                        @NotNull
                                        @Override
                                        public PyPullUpPresenter createPresenter(@NotNull final PyPullUpView view) {
                                          return new PyPullUpPresenterImpl(view, infoStorage, classUnderRefactoring);
                                        }

                                        @NotNull
                                        @Override
                                        public PyPullUpView createView(@NotNull final PyPullUpPresenter presenter) {
                                          return new PyPullUpViewSwingImpl(project, presenter, classUnderRefactoring, nothingToRefactor);
                                        }
                                      }
      );
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
