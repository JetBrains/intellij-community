// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * View configuration for "extract superclass"
 */
@ApiStatus.Internal
public final class PyExtractSuperclassInitializationInfo extends MembersViewInitializationInfo {

  private final @NotNull String myDefaultFilePath;
  private final VirtualFile @NotNull [] myRoots;

  /**
   * @param defaultFilePath module file path to display. User will be able to change it later.
   * @param roots           virtual files where user may add new module
   */
  PyExtractSuperclassInitializationInfo(final @NotNull MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel,
                                        final @NotNull Collection<PyMemberInfo<PyElement>> memberInfos,
                                        final @NotNull String defaultFilePath,
                                        final VirtualFile @NotNull ... roots) {
    super(memberInfoModel, memberInfos);
    myDefaultFilePath = defaultFilePath;
    myRoots = roots.clone();
  }

  public @NotNull @NlsSafe String getDefaultFilePath() {
    return myDefaultFilePath;
  }

  public VirtualFile @NotNull [] getRoots() {
    return myRoots.clone();
  }
}
