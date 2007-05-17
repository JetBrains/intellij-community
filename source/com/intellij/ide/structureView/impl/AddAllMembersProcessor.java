package com.intellij.ide.structureView.impl;

import com.intellij.psi.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.HashMap;

import java.util.List;
import java.util.Map;

/**
 * @deprecated use conflict-filter processor with dublicates resolver {@link com.intellij.psi.scope.processor.ConflictFilterProcessor}
 */
public class AddAllMembersProcessor extends BaseScopeProcessor {
  private List<PsiElement> myAllMembers;
  private PsiClass myPsiClass;
  private Map<MethodSignature,PsiMethod> myMethodsBySignature = new HashMap<MethodSignature, PsiMethod>();
  private MemberFilter myFilter;

  public AddAllMembersProcessor(List<PsiElement> allMembers, PsiClass psiClass) {
    this(allMembers, psiClass, ALL_ACCESSIBLE);
  }

  public AddAllMembersProcessor(List<PsiElement> allMembers, PsiClass psiClass, MemberFilter filter) {
    for (PsiElement psiElement : allMembers) {
      if (psiElement instanceof PsiMethod) mapMethodBySignature((PsiMethod)psiElement);
    }
    myAllMembers = allMembers;
    myPsiClass = psiClass;
    myFilter = filter;
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    PsiMember member = (PsiMember)element;
    if (!isInteresting(element)) return true;
    if (myPsiClass.isInterface() && isObjectMember(element)) return true;
    if (!myAllMembers.contains(member) && myFilter.isVisible(member, myPsiClass)) {
      if (member instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod)member;
        if (shouldAdd(psiMethod)) {
          mapMethodBySignature(psiMethod);
          myAllMembers.add(psiMethod);
        }
      }
      else {
        myAllMembers.add(member);
      }
    }
    return true;
  }

  private static boolean isObjectMember(PsiElement element) {
    if (!(element instanceof PsiMethod)) return false;
    final PsiClass containingClass = ((PsiMethod)element).getContainingClass();
    if (containingClass == null) {
      return false;
    }
    else {
      final String qualifiedName = containingClass.getQualifiedName();
      return qualifiedName != null && qualifiedName.equals(Object.class.getName());
    }
  }

  private void mapMethodBySignature(PsiMethod psiMethod) {
    myMethodsBySignature.put(psiMethod.getSignature(PsiSubstitutor.EMPTY), psiMethod);
  }

  private boolean shouldAdd(PsiMethod psiMethod) {
    MethodSignature signature = psiMethod.getSignature(PsiSubstitutor.EMPTY);
    PsiMethod previousMethod = myMethodsBySignature.get(signature);
    if (previousMethod == null) return true;
    if (isInheritor(psiMethod, previousMethod)) {
      myAllMembers.remove(previousMethod);
      return true;
    }
    return false;
  }

  private static boolean isInteresting(PsiElement element) {
    return element instanceof PsiMethod
           || element instanceof PsiField
           || element instanceof PsiClass
           || element instanceof PsiClassInitializer
      ;
  }

  public static boolean isInheritor(PsiMethod method, PsiMethod baseMethod) {
    return !isStatic(method) && !isStatic(baseMethod) && method.getContainingClass().isInheritor(baseMethod.getContainingClass(), true);
  }

  private static boolean isStatic(PsiMethod method) {
    return method.hasModifierProperty(PsiModifier.STATIC);
  }

  public abstract static class MemberFilter {
    public boolean isVisible(PsiMember element, PsiClass psiClass) {
      return !isInheritedConstructor(element, psiClass) && PsiUtil.isAccessible(element, psiClass, null) && isVisible(element);
    }

    private static boolean isInheritedConstructor(PsiMember member, PsiClass psiClass) {
      if (!(member instanceof PsiMethod))
        return false;
      PsiMethod method = (PsiMethod)member;
      return method.isConstructor() && method.getContainingClass() != psiClass;
    }

    protected abstract boolean isVisible(PsiModifierListOwner member);
  }

  public static final MemberFilter ALL_ACCESSIBLE = new MemberFilter() {
    protected boolean isVisible(PsiModifierListOwner member) {
      return true;
    }
  };

  public static final MemberFilter PUBLIC_ONLY = new MemberFilter() {
    protected boolean isVisible(PsiModifierListOwner member) {
      return member.hasModifierProperty(PsiModifier.PUBLIC);
    }
  };
}
