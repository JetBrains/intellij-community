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
import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

/**
 * Pull-up presenter implementation
 * @author Ilya.Kazakevich
 */
//TODO: Merge logic with "extract superclass" refactoring
class PyPullUpPresenterImpl extends AbstractUsesDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo> implements PyPullUpPresenter {
  @NotNull
  private final PyPullUpView myView;
  @NotNull
  private final PyMemberInfoStorage myStorage;
  @NotNull
  private final Collection<PyClass> myParents;

  /**
   * @param view view
   * @param infoStorage member storage
   * @param clazz class to refactor
   */
  PyPullUpPresenterImpl(@NotNull PyPullUpView view, @NotNull PyMemberInfoStorage infoStorage, @NotNull PyClass clazz) {
    super(clazz, null, false);
    this.myView = view;
    this.myStorage = infoStorage;
    myParents = PyAncestorsUtils.getAncestorsUnderUserControl(clazz);
    Preconditions.checkArgument(!myParents.isEmpty(), "No parents found");
  }


  @Override
  public void launch() {
    myView.init(myParents, this, myStorage.getClassMemberInfos(myClass));
  }

  //TODO: Mark Async ?
  @Override
  public void okClicked() {
    if (!isWritable()) {
      return; //TODO: Strange behaviour
    }

    MultiMap<PsiElement, String> conflicts = getConflicts();
    if (conflicts.isEmpty() || myView.showConflictsDialog(conflicts)) {
      pullUpWithHelper(myClass, myView.getSelectedMemberInfos(), myView.getSelectedParent());
      myView.closeDialog();
    }
  }


  private static void pullUpWithHelper(PyClass clazz, Collection<PyMemberInfo> selectedMemberInfos, PyClass superClass) {
    PsiNavigateUtil.navigate(PyPullUpHelper.pullUp(clazz, selectedMemberInfos, superClass));
  }


  public boolean isMemberEnabled(PyMemberInfo member) {
    PyClass currentSuperClass = myView.getSelectedParent();
    if (member.getMember() instanceof PyClass) {
      PyClass memberClass = (PyClass)member.getMember();
      if (memberClass.isSubclass(currentSuperClass) || currentSuperClass.isSubclass(memberClass)) {
        return false; //Class is already parent of superclass
      }
    }
    if (! PyPullUpConflictsUtil.checkConflicts(Arrays.asList(member), myView.getSelectedParent()).isEmpty()) {
      return false; //Member has conflict
    }
    return (!myStorage.getDuplicatedMemberInfos(currentSuperClass).contains(member)) && member.getMember() != currentSuperClass;
  }

  public boolean isAbstractEnabled(PyMemberInfo member) {
    return false;
  }

  public int checkForProblems(@NotNull PyMemberInfo member) {
    return member.isChecked() ? OK : super.checkForProblems(member);
  }

  @Override
  protected int doCheck(@NotNull PyMemberInfo memberInfo, int problem) {
    if (problem == ERROR && memberInfo.isStatic()) {
      return WARNING;
    }
    return problem;
  }

  private boolean isWritable() {
    Collection<PyMemberInfo> infos = myView.getSelectedMemberInfos();
    if (infos.size() == 0) {
      return true;
    }
    final PyElement element = infos.iterator().next().getMember();
    final Project project = element.getProject();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, myView.getSelectedParent())) return false;
    final PyClass container = PyUtil.getContainingClassOrSelf(element);
    if (container == null || !CommonRefactoringUtil.checkReadOnlyStatus(project, container)) return false;
    for (PyMemberInfo info : infos) {
      final PyElement member = info.getMember();
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, member)) return false;
    }
    return true;
  }


  private MultiMap<PsiElement, String> getConflicts() {
    final Collection<PyMemberInfo> infos = myView.getSelectedMemberInfos();
    PyClass superClass = myView.getSelectedParent();
    return PyPullUpConflictsUtil.checkConflicts(infos, superClass);
  }
}

