// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.pullUp;

import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for pull-up view
 */
@ApiStatus.Internal
public final class PyPullUpViewInitializationInfo extends MembersViewInitializationInfo {
  private final @NotNull Collection<PyClass> myParents;

  /**
   * @param parents list of possible parents to display.
   */
  PyPullUpViewInitializationInfo(final @NotNull MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel,
                                 final @NotNull List<PyMemberInfo<PyElement>> memberInfos,
                                 final @NotNull Collection<PyClass> parents) {
    super(memberInfoModel, memberInfos);
    myParents = new ArrayList<>(parents);
  }

  public @NotNull Collection<PyClass> getParents() {
    return Collections.unmodifiableCollection(myParents);
  }
}
