/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.classes;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.classMembers.ClassMembersRefactoringSupport;
import com.intellij.refactoring.classMembers.DependentMembersCollectorBase;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;

/**
 * @author Dennis.Ushakov
 */
public class PyClassMembersRefactoringSupport implements ClassMembersRefactoringSupport {  

  public static PyMemberInfoStorage getSelectedMemberInfos(PyClass clazz, PsiElement element1, PsiElement element2) {
    final PyMemberInfoStorage infoStorage = new PyMemberInfoStorage(clazz);
    for (PyMemberInfo member : infoStorage.getClassMemberInfos(clazz)) {
      final PyElement function = member.getMember();
      member.setChecked(PsiTreeUtil.isAncestor(function, element1, false) ||
                        PsiTreeUtil.isAncestor(function, element2, false));
    }
    return infoStorage;    
  }

  public DependentMembersCollectorBase createDependentMembersCollector(Object clazz, Object superClass) {
    return new PyDependentMembersCollector((PyClass)clazz, (PyClass)superClass);
  }

  public boolean isProperMember(MemberInfoBase member) {
    return true;
  }
}
