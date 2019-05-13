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

import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * All presenters that use members inherits this class.
 * <strong>Warning</strong>: Do not inherit it directly.
 * Check {@link com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenterNoPreviewImpl}
 * or {@link com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenterWithPreviewImpl} instead
 *
 * @param <T> view for that presenter
 * @param <M> Type of model {@link #myModel}
 * @author Ilya.Kazakevich
 */
abstract class MembersBasedPresenterImpl<T extends MembersBasedView<?>,
  M extends MemberInfoModel<PyElement, PyMemberInfo<PyElement>>> implements MembersBasedPresenter {
  @NotNull
  protected final T myView;
  @NotNull
  protected final PyClass myClassUnderRefactoring;
  @NotNull
  protected final PyMemberInfoStorage myStorage;
  /**
   * Member model
   */
  @NotNull
  protected final M myModel;

  /**
   * @param view                  View for presenter
   * @param classUnderRefactoring class to be refactored
   * @param infoStorage           info storage
   * @param model                 Member model (to be used for dependencies checking)
   */
  MembersBasedPresenterImpl(@NotNull final T view,
                            @NotNull final PyClass classUnderRefactoring,
                            @NotNull final PyMemberInfoStorage infoStorage,
                            @NotNull final M model) {
    myView = view;
    myClassUnderRefactoring = classUnderRefactoring;
    myStorage = infoStorage;
    myModel = model;
  }

  //TODO: Mark Async ?
  @Override
  public void okClicked() {
    final MultiMap<PyClass, PyMemberInfo<?>> conflicts = getConflicts();
    final Collection<PyMemberInfo<?>> dependencyConflicts = new ArrayList<>();
    for (final PyMemberInfo<PyElement> memberInfo : myStorage.getClassMemberInfos(myClassUnderRefactoring)) {
      if (myModel.checkForProblems(memberInfo) != MemberInfoModel.OK) {
        dependencyConflicts.add(memberInfo);
      }
    }

    if ((conflicts.isEmpty() && dependencyConflicts.isEmpty()) || myView.showConflictsDialog(conflicts, dependencyConflicts)) {
      try {
        validateView();
        doRefactor();
      }
      catch (final BadDataException e) {
        myView.showError(e.getMessage()); //Show error message if presenter says view in invalid
      }
    }
  }

  /**
   * Validates view (used by presenter to check if view is valid).
   * When overwrite, <strong>always</strong> call "super" <strong>first</strong>!
   * Throw {@link com.jetbrains.python.refactoring.classes.membersManager.vp.BadDataException} in case of error.
   * Do nothing, otherwise.
   * Method is designed to be overwritten and exception is used to simplify this process: children do not need parent's result.
   * They just call super.
   *
   * @throws BadDataException
   */
  protected void validateView() throws BadDataException {
    if (myView.getSelectedMemberInfos().isEmpty()) {
      throw new BadDataException(RefactoringBundle.message("no.members.selected"));
    }
  }

  /**
   * Does refactoring itself
   */
  abstract void doRefactor();

  /**
   * Checks if one of destination classes already has members that should be moved, so conflict would take place.
   *
   * @return map of conflicts (if any)
   * @see #getDestClassesToCheckConflicts()
   */
  @NotNull
  protected final MultiMap<PyClass, PyMemberInfo<?>> getConflicts() {
    final MultiMap<PyClass, PyMemberInfo<?>> result = new MultiMap<>();
    final Collection<PyMemberInfo<PyElement>> memberInfos = myView.getSelectedMemberInfos();
    for (final PyClass destinationClass : getDestClassesToCheckConflicts()) {
      for (final PyMemberInfo<PyElement> pyMemberInfo : memberInfos) {
        if (pyMemberInfo.hasConflict(destinationClass)) {
          result.putValue(destinationClass, pyMemberInfo);
        }
      }
    }
    return result;
  }

  /**
   * @return classes where this refactoring will move members. To be used to check for conflicts (if one of target classes already has members)
   * @see #getConflicts()
   */
  @NotNull
  protected abstract Iterable<? extends PyClass> getDestClassesToCheckConflicts();
}
