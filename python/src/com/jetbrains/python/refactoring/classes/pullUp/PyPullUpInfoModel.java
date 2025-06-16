// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.pullUp;

import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Dependencies model for PyPullUp refactoring
*/
@ApiStatus.Internal
public final class PyPullUpInfoModel extends AbstractUsesDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo<PyElement>> {
  private final @NotNull PyPullUpView myView;

  public PyPullUpInfoModel(final @NotNull PyClass classUnderRefactoring,
                    final @NotNull PyPullUpView view) {
    super(classUnderRefactoring, null, false);
    myView = view;
  }

  @Override
  public boolean isAbstractEnabled(final PyMemberInfo<PyElement> member) {
    return member.isCouldBeAbstract() && isMemberEnabled(member); // TODO: copy paste with other models, get rid of
  }

  @Override
  public int checkForProblems(final @NotNull PyMemberInfo<PyElement> member) {
    return member.isChecked() ? OK : super.checkForProblems(member);
  }

  @Override
  protected int doCheck(final @NotNull PyMemberInfo<PyElement> memberInfo, final int problem) {
    return problem;
  }

  @Override
  public boolean isMemberEnabled(final PyMemberInfo<PyElement> member) {
    final PyClass currentSuperClass = myView.getSelectedParent();
    if (member.getMember() instanceof PyClass memberClass) {
      //TODO: Delegate to Memebers Managers
      if (memberClass.isSubclass(currentSuperClass, null) || currentSuperClass.isSubclass(memberClass, null)) {
        return false; //Class is already parent of superclass
      }
    }
    return true;
  }
}
