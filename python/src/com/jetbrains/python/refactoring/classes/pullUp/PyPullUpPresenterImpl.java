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

import com.google.common.base.Preconditions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenterWithPreviewImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * Pull-up presenter implementation
 *
 * @author Ilya.Kazakevich
 */
class PyPullUpPresenterImpl extends MembersBasedPresenterWithPreviewImpl<PyPullUpView> implements PyPullUpPresenter {
  @NotNull
  private final Collection<PyClass> myParents;

  /**
   * @param view        view
   * @param infoStorage member storage
   * @param clazz       class to refactor
   */
  PyPullUpPresenterImpl(@NotNull final PyPullUpView view, @NotNull final PyMemberInfoStorage infoStorage, @NotNull final PyClass clazz) {
    super(view, clazz, infoStorage);
    myParents = PyAncestorsUtils.getAncestorsUnderUserControl(clazz);
    Preconditions.checkArgument(!myParents.isEmpty(), "No parents found");
  }


  @Override
  public void launch() {
    myView.configure(
      new PyPullUpViewInitializationInfo(new PyPullUpInfoModel(), myStorage.getClassMemberInfos(myClassUnderRefactoring), myParents));
    myView.initAndShow();
  }

  @Override
  public void okClicked() {
    if (!isWritable()) {
      return; //TODO: Strange behaviour
    }
    super.okClicked();
  }

  @NotNull
  @Override
  public BaseRefactoringProcessor createProcessor() {
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
  @NotNull
  public MultiMap<PsiElement, String> getConflicts() {
    final Collection<PyMemberInfo<PyElement>> infos = myView.getSelectedMemberInfos();
    final PyClass superClass = myView.getSelectedParent();
    return PyPullUpConflictsUtil.checkConflicts(infos, superClass);
  }

  private class PyPullUpInfoModel extends AbstractUsesDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo<PyElement>> {

    PyPullUpInfoModel() {
      super(myClassUnderRefactoring, null, false);
    }

    @Override
    public boolean isAbstractEnabled(final PyMemberInfo<PyElement> member) {
      return member.isCouldBeAbstract() && isMemberEnabled(member); // TODO: copy paste with other models, get rid of
    }

    @Override
    public int checkForProblems(@NotNull final PyMemberInfo<PyElement> member) {
      return member.isChecked() ? OK : super.checkForProblems(member);
    }


    @Override
    protected int doCheck(@NotNull final PyMemberInfo<PyElement> memberInfo, final int problem) {
      if (problem == ERROR && memberInfo.isStatic()) {
        return WARNING;
      }
      return problem;
    }

    @Override
    public boolean isMemberEnabled(final PyMemberInfo<PyElement> member) {
      final PyClass currentSuperClass = myView.getSelectedParent();
      if (member.getMember() instanceof PyClass) {
        //TODO: Delegate to Memebers Managers
        final PyClass memberClass = (PyClass)member.getMember();
        if (memberClass.isSubclass(currentSuperClass) || currentSuperClass.isSubclass(memberClass)) {
          return false; //Class is already parent of superclass
        }
      }
      if (!PyPullUpConflictsUtil.checkConflicts(Collections.singletonList(member), myView.getSelectedParent()).isEmpty()) {
        return false; //Member has conflict
      }
      return (!myStorage.getDuplicatedMemberInfos(currentSuperClass).contains(member)) && member.getMember() != currentSuperClass;
    }
  }
}

