// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.pullUp;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

/**
 * Displays "nothing to refactor" message
 * @author Ilya.Kazakevich
 */
class PyPullUpNothingToRefactorMessage {

  private final @NotNull Project myProject;
  private final @NotNull Editor myEditor;
  private final @NotNull PyClass myClassUnderRefactoring;

  /**
   *
   * @param project project to be used
   * @param editor editor to be used
   * @param classUnderRefactoring class user refactors
   */
  PyPullUpNothingToRefactorMessage(final @NotNull Project project,
                                   final @NotNull Editor editor,
                                   final @NotNull PyClass classUnderRefactoring) {
    myProject = project;
    myEditor = editor;
    myClassUnderRefactoring = classUnderRefactoring;
  }

  /**
   * Display message
   */
  void showNothingToRefactor() {
    CommonRefactoringUtil.showErrorHint(myProject, myEditor, PyBundle
                                          .message("refactoring.pull.up.error.cannot.perform.refactoring.no.base.classes",
                                                   myClassUnderRefactoring.getName()), RefactoringBundle.message("pull.members.up.title"),
                                        "members.pull.up"
    );
  }
}
