/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.move.moduleMembers;

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
  PyModuleMemberInfo(PyElement member) {
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
