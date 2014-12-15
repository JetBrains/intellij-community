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

  @NotNull
  private final Project myProject;
  @NotNull
  private final Editor myEditor;
  @NotNull
  private final PyClass myClassUnderRefactoring;

  /**
   *
   * @param project project to be used
   * @param editor editor to be used
   * @param classUnderRefactoring class user refactors
   */
  PyPullUpNothingToRefactorMessage(@NotNull final Project project,
                                   @NotNull final Editor editor,
                                   @NotNull final PyClass classUnderRefactoring) {
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
