package com.jetbrains.python.refactoring.classes.pullUp;

import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for pull-up view
 *
 * @author Ilya.Kazakevich
 */
class PyPullUpViewInitializationInfo extends MembersViewInitializationInfo {
  @NotNull
  private final Collection<PyClass> myParents;

  /**
   * @param parents list of possible parents to display.
   */
  PyPullUpViewInitializationInfo(@NotNull final MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel,
                                 @NotNull final List<PyMemberInfo<PyElement>> memberInfos,
                                 @NotNull final Collection<PyClass> parents) {
    super(memberInfoModel, memberInfos);
    myParents = new ArrayList<PyClass>(parents);
  }

  @NotNull
  public Collection<PyClass> getParents() {
    return Collections.unmodifiableCollection(myParents);
  }
}
