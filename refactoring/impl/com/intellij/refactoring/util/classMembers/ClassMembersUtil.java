package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;

public class ClassMembersUtil {
  public static boolean isProperMember(MemberInfo memberInfo) {
    final PsiElement member = memberInfo.getMember();
    return member instanceof PsiField || member instanceof PsiMethod
                || (member instanceof PsiClass && memberInfo.getOverrides() == null);
  }

  public static boolean isImplementedInterface(MemberInfo memberInfo) {
    return memberInfo.getMember() instanceof PsiClass && Boolean.FALSE.equals(memberInfo.getOverrides());
  }
}
