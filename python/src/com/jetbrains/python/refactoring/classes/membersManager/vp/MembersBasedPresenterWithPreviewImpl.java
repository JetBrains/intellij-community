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
package com.jetbrains.python.refactoring.classes.membersManager.vp;

import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Does refactoring with preview (based on {@link com.intellij.refactoring.BaseRefactoringProcessor}).
 * Child must implement {@link #createProcessor()} and return appropriate processor.
 * "Preview" button would be displayed.
 *
 * @param <T> view for this presenter
 * @param <M> Type of model
 * @author Ilya.Kazakevich
 */
public abstract class MembersBasedPresenterWithPreviewImpl<T extends MembersBasedView<?>,
  M extends MemberInfoModel<PyElement, PyMemberInfo<PyElement>>> extends MembersBasedPresenterImpl<T, M> {


  /**
   * @param view                  view for this presenter
   * @param classUnderRefactoring class to refactor
   * @param infoStorage           info storage
   * @param model                 Member model (to be used for dependencies checking)
   */
  protected MembersBasedPresenterWithPreviewImpl(@NotNull final T view,
                                                 @NotNull final PyClass classUnderRefactoring,
                                                 @NotNull final PyMemberInfoStorage infoStorage,
                                                 @NotNull final M  model) {
    super(view, classUnderRefactoring, infoStorage, model);
  }

  @Override
  public boolean showPreview() {
    return true;
  }

  @Override
  protected void doRefactor() {
    myView.invokeRefactoring(createProcessor());
  }

  /**
   * @return processor for refactoring
   */
  @NotNull
  public abstract BaseRefactoringProcessor createProcessor();
}
