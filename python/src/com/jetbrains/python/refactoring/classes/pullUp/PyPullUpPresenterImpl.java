// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.pullUp;

import com.google.common.base.Preconditions;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenterWithPreviewImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.Collections;

/**
 * Pull-up presenter implementation
 */
@ApiStatus.Internal
public final class PyPullUpPresenterImpl extends MembersBasedPresenterWithPreviewImpl<PyPullUpView, PyPullUpInfoModel> implements PyPullUpPresenter {
  private final @NotNull Collection<PyClass> myParents;

  /**
   * @param view        view
   * @param infoStorage member storage
   * @param clazz       class to refactor
   */
  @VisibleForTesting
  public PyPullUpPresenterImpl(final @NotNull PyPullUpView view, final @NotNull PyMemberInfoStorage infoStorage, final @NotNull PyClass clazz) {
    super(view, clazz, infoStorage, new PyPullUpInfoModel(clazz, view));
    myParents = PyAncestorsUtils.getAncestorsUnderUserControl(clazz);
    Preconditions.checkArgument(!myParents.isEmpty(), "No parents found");
  }

  @Override
  public void launch() {
    myView.configure(
      new PyPullUpViewInitializationInfo(myModel, myStorage.getClassMemberInfos(myClassUnderRefactoring), myParents));

    // If there is no enabled member then only error should be displayed

    boolean atLeastOneEnabled = false;
    for (final PyMemberInfo<PyElement> info : myStorage.getClassMemberInfos(myClassUnderRefactoring)) {
      if (myModel.isMemberEnabled(info)) {
        atLeastOneEnabled = true;
      }
    }


    if (atLeastOneEnabled) {
      myView.initAndShow();
    } else {
      myView.showNothingToRefactor();
    }
  }

  @Override
  public void okClicked() {
    if (!isWritable()) {
      return; //TODO: Strange behaviour
    }
    super.okClicked();
  }

  @Override
  public @NotNull BaseRefactoringProcessor createProcessor() {
    return new PyPullUpProcessor(myClassUnderRefactoring, myView.getSelectedParent(), myView.getSelectedMemberInfos());
  }

  private boolean isWritable() {
    final Collection<PyMemberInfo<PyElement>> infos = myView.getSelectedMemberInfos();
    if (infos.isEmpty()) {
      return true;
    }
    final PyElement element = infos.iterator().next().getMember();
    final Project project = element.getProject();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, myView.getSelectedParent())) return false;
    final PyClass container = PyUtil.getContainingClassOrSelf(element);
    if (container == null || !CommonRefactoringUtil.checkReadOnlyStatus(project, container)) return false;
    for (final PyMemberInfo<PyElement> info : infos) {
      final PyElement member = info.getMember();
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, member)) return false;
    }
    return true;
  }

  @Override
  public void parentChanged() {
    myModel.setSuperClass(myView.getSelectedParent());
  }

  @Override
  protected @NotNull Iterable<? extends PyClass> getDestClassesToCheckConflicts() {
    return Collections.singletonList(myView.getSelectedParent());
  }
}

