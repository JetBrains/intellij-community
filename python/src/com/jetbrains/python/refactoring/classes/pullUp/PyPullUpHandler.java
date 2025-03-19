// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.pullUp;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringHandler;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.vp.Creator;
import com.jetbrains.python.vp.ViewPresenterUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class PyPullUpHandler extends PyClassRefactoringHandler {
  @Override
  protected void doRefactorImpl(final @NotNull Project project,
                                final @NotNull PyClass classUnderRefactoring,
                                final @NotNull PyMemberInfoStorage infoStorage,
                                final @NotNull Editor editor) {
    //TODO: Move to vp (presenter) as well
    final PyPullUpNothingToRefactorMessage nothingToRefactor = new PyPullUpNothingToRefactorMessage(project, editor, classUnderRefactoring);

    if (PyAncestorsUtils.getAncestorsUnderUserControl(classUnderRefactoring).isEmpty()) {
      nothingToRefactor.showNothingToRefactor();
      return;
    }


    ViewPresenterUtils
      .linkViewWithPresenterAndLaunch(PyPullUpPresenter.class, PyPullUpView.class, new Creator<>() {
                                        @Override
                                        public @NotNull PyPullUpPresenter createPresenter(final @NotNull PyPullUpView view) {
                                          return new PyPullUpPresenterImpl(view, infoStorage, classUnderRefactoring);
                                        }

                                        @Override
                                        public @NotNull PyPullUpView createView(final @NotNull PyPullUpPresenter presenter) {
                                          return new PyPullUpViewSwingImpl(project, presenter, classUnderRefactoring, nothingToRefactor);
                                        }
                                      }
      );
  }


  @Override
  protected String getTitle() {
    return getRefactoringName();
  }

  @Override
  protected String getHelpId() {
    return "refactoring.pullMembersUp";
  }

  public static @Nls String getRefactoringName() {
    return PyBundle.message("refactoring.pull.up.dialog.title");
  }
}
