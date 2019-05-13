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
    myParents = new ArrayList<>(parents);
  }

  @NotNull
  public Collection<PyClass> getParents() {
    return Collections.unmodifiableCollection(myParents);
  }
}
