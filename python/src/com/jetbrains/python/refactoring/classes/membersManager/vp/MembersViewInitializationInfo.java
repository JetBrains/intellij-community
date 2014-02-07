package com.jetbrains.python.refactoring.classes.membersManager.vp;

import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Configuration for {@link com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedView}
 *
 * @author Ilya.Kazakevich
 */
public class MembersViewInitializationInfo {

  @NotNull
  private final MemberInfoModel<PyElement, PyMemberInfo> myMemberInfoModel;
  @NotNull
  private final Collection<PyMemberInfo> myMemberInfos;

  /**
   * @param memberInfoModel model to be used in members panel
   * @param memberInfos     members to displau
   */
  public MembersViewInitializationInfo(@NotNull final MemberInfoModel<PyElement, PyMemberInfo> memberInfoModel,
                                       @NotNull final Collection<PyMemberInfo> memberInfos) {
    myMemberInfos = new ArrayList<PyMemberInfo>(memberInfos);
    myMemberInfoModel = memberInfoModel;
  }

  /**
   * @return model to be used in members panel
   */
  @NotNull
  public MemberInfoModel<PyElement, PyMemberInfo> getMemberInfoModel() {
    return myMemberInfoModel;
  }

  /**
   * @return members to display
   */
  @NotNull
  public Collection<PyMemberInfo> getMemberInfos() {
    return Collections.unmodifiableCollection(myMemberInfos);
  }
}
