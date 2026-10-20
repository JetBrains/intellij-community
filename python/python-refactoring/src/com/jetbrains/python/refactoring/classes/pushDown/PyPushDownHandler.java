// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Query;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringHandler;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.vp.Creator;
import com.jetbrains.python.vp.ViewPresenterUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownHandler extends PyClassRefactoringHandler {
  @Override
  protected void doRefactorImpl(final @NotNull Project project,
                                final @NotNull PyClass classUnderRefactoring,
                                final @NotNull PyMemberInfoStorage infoStorage,
                                @NotNull Editor editor) {

    //TODO: Move to presenter?
    final Query<PyClass> query = PyClassInheritorsSearch.search(classUnderRefactoring, false);
    if (query.findFirst() == null) {
      final String message = RefactoringBundle.message("class.0.does.not.have.inheritors", classUnderRefactoring.getName());
      CommonRefactoringUtil.showErrorHint(project, editor, message, getTitle(), getHelpId());
      return;
    }

    ViewPresenterUtils
      .linkViewWithPresenterAndLaunch(PyPushDownPresenter.class, PyPushDownView.class, new Creator<>() {
        @Override
        public @NotNull PyPushDownPresenter createPresenter(@NotNull PyPushDownView view) {
          return new PyPushDownPresenterImpl(project, view, classUnderRefactoring, infoStorage);
        }

        @Override
        public @NotNull PyPushDownView createView(@NotNull PyPushDownPresenter presenter) {
          return new PyPushDownViewSwingImpl(classUnderRefactoring, project, presenter);
        }
      });
  }

  @Override
  protected @DialogTitle String getTitle() {
    return getRefactoringName();
  }

  @Override
  protected String getHelpId() {
    return "members.push.down";
  }

  public static @Nls String getRefactoringName() {
    return RefactoringBundle.message("push.members.down.title");
  }
}
