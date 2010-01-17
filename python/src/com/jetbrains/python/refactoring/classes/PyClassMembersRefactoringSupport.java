package com.jetbrains.python.refactoring.classes;

import com.intellij.psi.PsiElement;
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
      member.setChecked(function.getTextOffset() >= element1.getTextOffset() &&
                        function.getTextOffset() + function.getTextLength() <= element2.getTextOffset() + element2.getTextLength());
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
