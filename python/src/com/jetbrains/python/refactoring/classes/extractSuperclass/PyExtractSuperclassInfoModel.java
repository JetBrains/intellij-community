// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya.Kazakevich
 */
class PyExtractSuperclassInfoModel extends AbstractUsesDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo<PyElement>> {
  PyExtractSuperclassInfoModel(final @NotNull PyClass clazz) {
    super(clazz, null, false);
  }

  @Override
  public boolean isAbstractEnabled(final PyMemberInfo<PyElement> member) {
    if (propertyDependsOnThisMethod(member)) {
      return false;
    }
    return member.isCouldBeAbstract() && isMemberEnabled(member);
  }

  /**
   * If property depends on method, this method can't be made abstract
   */
  private boolean propertyDependsOnThisMethod(@NotNull PyMemberInfo<PyElement> member) {
    var dependencies = myMemberDependencyGraph.getDependenciesOf(member.getMember());
    return (dependencies != null && dependencies.stream().anyMatch(o -> o instanceof PyTargetExpression));
  }

  @Override
  public int checkForProblems(final @NotNull PyMemberInfo<PyElement> member) {
    return member.isChecked() ? OK : super.checkForProblems(member);
  }

  @Override
  protected int doCheck(final @NotNull PyMemberInfo<PyElement> memberInfo, final int problem) {
    return problem;
  }
}
