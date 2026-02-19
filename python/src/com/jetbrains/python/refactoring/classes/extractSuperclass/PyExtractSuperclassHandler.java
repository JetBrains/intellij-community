// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringHandler;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMembersUtil;
import com.jetbrains.python.vp.Creator;
import com.jetbrains.python.vp.ViewPresenterUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dennis.Ushakov
 */
public class PyExtractSuperclassHandler extends PyClassRefactoringHandler {
  @Override
  protected void doRefactorImpl(final @NotNull Project project,
                                final @NotNull PyClass classUnderRefactoring,
                                final @NotNull PyMemberInfoStorage infoStorage,
                                final @NotNull Editor editor) {
    //TODO: Move to presenter
    if (PyMembersUtil.filterOutObject(infoStorage.getClassMemberInfos(classUnderRefactoring)).isEmpty()) {
      CommonRefactoringUtil.showErrorHint(project, editor, PyBundle
        .message("refactoring.extract.super.class.no.members.allowed"), RefactoringBundle.message("extract.superclass.elements.header"),
                                          null);
      return;
    }

    ViewPresenterUtils.linkViewWithPresenterAndLaunch(PyExtractSuperclassPresenter.class, PyExtractSuperclassView.class,
                                                      new Creator<>() {
                                                        @Override
                                                        public @NotNull PyExtractSuperclassPresenter createPresenter(final @NotNull PyExtractSuperclassView view) {
                                                          return new PyExtractSuperclassPresenterImpl(view, classUnderRefactoring,
                                                                                                      infoStorage);
                                                        }

                                                        @Override
                                                        public @NotNull PyExtractSuperclassView createView(final @NotNull PyExtractSuperclassPresenter presenter) {
                                                          return new PyExtractSuperclassViewSwingImpl(classUnderRefactoring, project,
                                                                                                      presenter);
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
    return "refactoring.extractSuperclass";
  }

  public static @Nls(capitalization = Nls.Capitalization.Title) String getRefactoringName() {
    return RefactoringBundle.message("extract.superclass.title");
  }
}
