/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * View configuration for "extract superclass"
 *
 * @author Ilya.Kazakevich
 */
class PyExtractSuperclassInitializationInfo extends MembersViewInitializationInfo {

  @NotNull
  private final String myDefaultFilePath;
  @NotNull
  private final VirtualFile[] myRoots;

  /**
   * @param defaultFilePath module file path to display. User will be able to change it later.
   * @param roots           virtual files where user may add new module
   */
  PyExtractSuperclassInitializationInfo(@NotNull final MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel,
                                        @NotNull final Collection<PyMemberInfo<PyElement>> memberInfos,
                                        @NotNull final String defaultFilePath,
                                        @NotNull final VirtualFile... roots) {
    super(memberInfoModel, memberInfos);
    myDefaultFilePath = defaultFilePath;
    myRoots = roots.clone();
  }

  @NotNull
  public String getDefaultFilePath() {
    return myDefaultFilePath;
  }

  @NotNull
  public VirtualFile[] getRoots() {
    return myRoots.clone();
  }
}
