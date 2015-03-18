package com.jetbrains.python.refactoring.move;

import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
class PyModuleMemberInfoModel extends AbstractUsesDependencyMemberInfoModel<PyElement, PyFile, PyModuleMemberInfo> {
  final PyFile myPyFile;

  public PyModuleMemberInfoModel(@NotNull PyFile file) {
    super(file, null, false);
    myPyFile = file;
  }

  @Override
  public boolean isAbstractEnabled(PyModuleMemberInfo member) {
    return false;
  }

  @Override
  protected int doCheck(@NotNull PyModuleMemberInfo memberInfo, int problem) {
    return problem == ERROR ? WARNING : problem;
  }
}
