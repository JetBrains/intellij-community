package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya.Kazakevich
 */
class PyExtractSuperclassInfoModel extends AbstractUsesDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo> {
  PyExtractSuperclassInfoModel(@NotNull final PyClass clazz) {
    super(clazz, null, false);
  }

  @Override
  public boolean isAbstractEnabled(final PyMemberInfo member) {
    return false;
  }

  @Override
  public int checkForProblems(@NotNull final PyMemberInfo member) {
    return member.isChecked() ? OK : super.checkForProblems(member);
  }

  @Override
  protected int doCheck(@NotNull final PyMemberInfo memberInfo, final int problem) {
    return problem;
  }
}
