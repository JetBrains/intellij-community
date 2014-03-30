package com.jetbrains.python.refactoring.classes;

import com.google.common.base.Function;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

/**
* @author Ilya.Kazakevich
*/
public class NameAndStatusTransformer implements Function<PyMemberInfo<PyElement>, PyPresenterTestMemberEntry> {
  @NotNull
  private final MemberInfoModel<PyElement, PyMemberInfo<PyElement>> myMemberInfoModel;

  public NameAndStatusTransformer(@NotNull final MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel) {
    myMemberInfoModel = memberInfoModel;
  }

  @Override
  public PyPresenterTestMemberEntry apply(final PyMemberInfo<PyElement> input) {
    return new PyPresenterTestMemberEntry(input.getDisplayName(), myMemberInfoModel.isMemberEnabled(input), input.isStatic(), myMemberInfoModel.isAbstractEnabled(input));
  }
}
