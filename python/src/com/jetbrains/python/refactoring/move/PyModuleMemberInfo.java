package com.jetbrains.python.refactoring.move;

import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.jetbrains.python.psi.PyElement;

/**
 * Helper object that describes top-level symbol of the module (class, function or assignment) for
 * the table in "Move ..." dialog. Concrete properties of given symbol is described in {@link PyModuleMemberInfoModel}.
 *
 * @author Mikhail Golubev
 * @see PyModuleMemberInfoModel
 */
class PyModuleMemberInfo extends MemberInfoBase<PyElement> {
  public PyModuleMemberInfo(PyElement member) {
    super(member);
  }

  @Override
  public boolean isStatic() {
    return true;
  }

  @Override
  public String getDisplayName() {
    return getMember().getName();
  }

  @Override
  public String toString() {
    return "PyModuleMemberInfo(" + getMember() + ")";
  }
}
