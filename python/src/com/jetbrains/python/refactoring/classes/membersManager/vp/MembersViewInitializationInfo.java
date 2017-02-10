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
  private final MemberInfoModel<PyElement, PyMemberInfo<PyElement>> myMemberInfoModel;
  @NotNull
  private final Collection<PyMemberInfo<PyElement>> myMemberInfos;

  /**
   * @param memberInfoModel model to be used in members panel
   * @param memberInfos     members to displau
   */
  public MembersViewInitializationInfo(@NotNull final MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel,
                                       @NotNull final Collection<PyMemberInfo<PyElement>> memberInfos) {
    myMemberInfos = new ArrayList<>(memberInfos);
    myMemberInfoModel = memberInfoModel;
  }

  /**
   * @return model to be used in members panel
   */
  @NotNull
  public MemberInfoModel<PyElement, PyMemberInfo<PyElement>> getMemberInfoModel() {
    return myMemberInfoModel;
  }

  /**
   * @return members to display
   */
  @NotNull
  public Collection<PyMemberInfo<PyElement>> getMemberInfos() {
    return Collections.unmodifiableCollection(myMemberInfos);
  }
}
