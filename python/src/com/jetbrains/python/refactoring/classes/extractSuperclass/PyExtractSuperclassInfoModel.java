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

import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya.Kazakevich
 */
class PyExtractSuperclassInfoModel extends AbstractUsesDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo<PyElement>> {
  PyExtractSuperclassInfoModel(@NotNull final PyClass clazz) {
    super(clazz, null, false);
  }

  @Override
  public boolean isAbstractEnabled(final PyMemberInfo<PyElement> member) {
    return member.isCouldBeAbstract() && isMemberEnabled(member);
  }

  @Override
  public int checkForProblems(@NotNull final PyMemberInfo<PyElement> member) {
    return member.isChecked() ? OK : super.checkForProblems(member);
  }

  @Override
  protected int doCheck(@NotNull final PyMemberInfo<PyElement> memberInfo, final int problem) {
    return problem;
  }
}
