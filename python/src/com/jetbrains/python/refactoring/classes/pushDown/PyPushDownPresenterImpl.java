// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.classMembers.UsedByDependencyMemberInfoModel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.PyMembersUtil;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenterWithPreviewImpl;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya.Kazakevich
 */
public class PyPushDownPresenterImpl extends MembersBasedPresenterWithPreviewImpl<PyPushDownView, MemberInfoModel<PyElement, PyMemberInfo<PyElement>>> implements PyPushDownPresenter {
  private final @NotNull Project myProject;

  public PyPushDownPresenterImpl(final @NotNull Project project,
                                 final @NotNull PyPushDownView view,
                                 final @NotNull PyClass classUnderRefactoring,
                                 final @NotNull PyMemberInfoStorage infoStorage) {
    super(view, classUnderRefactoring, infoStorage, new UsedByDependencyMemberInfoModel<>(classUnderRefactoring));
    myProject = project;
  }

  @Override
  public @NotNull BaseRefactoringProcessor createProcessor() {
    return new PyPushDownProcessor(myProject, myView.getSelectedMemberInfos(), myClassUnderRefactoring);
  }

  @Override
  protected @NotNull Iterable<? extends PyClass> getDestClassesToCheckConflicts() {
    return PyPushDownProcessor.getInheritors(myClassUnderRefactoring);
  }

  @Override
  public void launch() {
    myView
      .configure(new MembersViewInitializationInfo(myModel, PyMembersUtil
        .filterOutObject(myStorage.getClassMemberInfos(myClassUnderRefactoring))));
    myView.initAndShow();
  }
}
