package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.refactoring.classMembers.AbstractMemberInfoStorage;

import java.util.ArrayList;


public class MemberInfoStorage extends AbstractMemberInfoStorage<PsiMember, PsiClass, MemberInfo> {

  public MemberInfoStorage(PsiClass aClass, MemberInfo.Filter<PsiMember> memberInfoFilter) {
    super(aClass, memberInfoFilter);
  }

  @Override
  protected boolean isInheritor(PsiClass baseClass, PsiClass aClass) {
    return aClass.isInheritor(baseClass, true);
  }

  @Override
  protected void extractClassMembers(PsiClass aClass, ArrayList<MemberInfo> temp) {
    MemberInfo.extractClassMembers(aClass, temp, myFilter, false);
  }

  @Override
  protected boolean memberConflict(PsiElement member1, PsiElement member) {
    if(member instanceof PsiMethod && member1 instanceof PsiMethod) {
      return MethodSignatureUtil.areSignaturesEqual((PsiMethod) member, (PsiMethod) member1);
    }
    else if(member instanceof PsiField && member1 instanceof PsiField
            || member instanceof PsiClass && member1 instanceof PsiClass) {
      return ((PsiNamedElement) member).getName().equals(((PsiNamedElement) member1).getName());
    }
    return false;
  }


  @Override
  protected void buildSubClassesMap(PsiClass aClass) {
    final PsiReferenceList extendsList = aClass.getExtendsList();
    if (extendsList != null) {
      buildSubClassesMapForList(extendsList.getReferencedTypes(), aClass);
    }
    final PsiReferenceList implementsList = aClass.getImplementsList();
    if (implementsList != null) {
      buildSubClassesMapForList(implementsList.getReferencedTypes(), aClass);
    }
  }

  private void buildSubClassesMapForList(final PsiClassType[] classesList, PsiClass aClass) {
    for (int i = 0; i < classesList.length; i++) {
      PsiClassType element = classesList[i];
      PsiClass resolved = element.resolve();
      if(resolved != null) {
        PsiClass superClass = resolved;
        getSubclasses(superClass).add(aClass);
        buildSubClassesMap(superClass);
      }
    }
  }
}
