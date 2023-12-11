// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.classes;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.classMembers.ClassMembersRefactoringSupport;
import com.intellij.refactoring.classMembers.DependentMembersCollectorBase;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.move.moduleMembers.PyDependentModuleMembersCollector;

/**
 * @author Dennis.Ushakov
 */
public final class PyMembersRefactoringSupport implements ClassMembersRefactoringSupport {

  public static PyMemberInfoStorage getSelectedMemberInfos(PyClass clazz, PsiElement element1, PsiElement element2) {
    final PyMemberInfoStorage infoStorage = new PyMemberInfoStorage(clazz);
    for (PyMemberInfo<PyElement> member : infoStorage.getClassMemberInfos(clazz)) {
      final PyElement function = member.getMember();
      member.setChecked(PsiTreeUtil.isAncestor(function, element1, false) ||
                        PsiTreeUtil.isAncestor(function, element2, false));
    }
    return infoStorage;
  }

  @Override
  public DependentMembersCollectorBase createDependentMembersCollector(Object clazz, Object superClass) {
    if (clazz instanceof PyClass) {
      return new PyDependentClassMembersCollector((PyClass)clazz, (PyClass)superClass);
    }
    else if (clazz instanceof PyFile) {
      return new PyDependentModuleMembersCollector(((PyFile)clazz));
    }
    return null;
  }

  @Override
  public boolean isProperMember(MemberInfoBase member) {
    return true;
  }
}
