package com.intellij.lang.java;

import com.intellij.psi.PsiClass;
import com.intellij.refactoring.classMembers.ClassMembersRefactoringSupport;
import com.intellij.refactoring.classMembers.DependentMembersCollectorBase;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.util.classMembers.ClassMembersUtil;
import com.intellij.refactoring.util.classMembers.DependentMembersCollector;

/**
 * @author Dennis.Ushakov
 */
public class JavaClassMembersRefactoringSupport implements ClassMembersRefactoringSupport {
  public DependentMembersCollectorBase createDependentMembersCollector(Object clazz, Object superClass) {
    return new DependentMembersCollector((PsiClass)clazz, (PsiClass)superClass);
  }

  public boolean isProperMember(MemberInfoBase member) {
    return ClassMembersUtil.isProperMember(member);
  }
}
