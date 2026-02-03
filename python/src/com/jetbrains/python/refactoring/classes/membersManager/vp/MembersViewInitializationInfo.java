// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.membersManager.vp;

import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Configuration for {@link MembersBasedView}
 *
 * @author Ilya.Kazakevich
 */
public class MembersViewInitializationInfo {

  private final @NotNull MemberInfoModel<PyElement, PyMemberInfo<PyElement>> myMemberInfoModel;
  private final @NotNull Collection<PyMemberInfo<PyElement>> myMemberInfos;

  /**
   * @param memberInfoModel model to be used in members panel
   * @param memberInfos     members to display
   */
  public MembersViewInitializationInfo(final @NotNull MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel,
                                       final @NotNull Collection<PyMemberInfo<PyElement>> memberInfos) {
    myMemberInfos = new ArrayList<>(memberInfos);
    myMemberInfoModel = memberInfoModel;
  }

  /**
   * @return model to be used in members panel
   */
  public @NotNull MemberInfoModel<PyElement, PyMemberInfo<PyElement>> getMemberInfoModel() {
    return myMemberInfoModel;
  }

  /**
   * @return members to display
   */
  public @NotNull Collection<PyMemberInfo<PyElement>> getMemberInfos() {
    return Collections.unmodifiableCollection(myMemberInfos);
  }
}
