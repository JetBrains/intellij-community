package com.intellij.ide.structureView.impl;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @deprecated use conflict-filter processor with dublicates resolver {@link com.intellij.psi.scope.processor.ConflictFilterProcessor}
 */
public class AddAllMembersProcessor extends BaseScopeProcessor {
  private ArrayList myAllMembers;
  private PsiClass myPsiClass;
  private Map myMethodsBySignature = new com.intellij.util.containers.HashMap();
  private MemberFilter myFilter;

  public AddAllMembersProcessor(ArrayList allMembers, PsiClass psiClass) {
    this(allMembers, psiClass, ALL_ACCESSIBLE);
  }

  public AddAllMembersProcessor(ArrayList allMembers, PsiClass psiClass, MemberFilter filter) {
    for (Iterator iterator = allMembers.iterator(); iterator.hasNext();) {
      PsiElement psiElement = (PsiElement) iterator.next();
      if (psiElement instanceof PsiMethod)
        mapMethodBySignature((PsiMethod) psiElement);
    }
    myAllMembers = allMembers;
    myPsiClass = psiClass;
    myFilter = filter;
  }

  public void handleEvent(PsiScopeProcessor.Event event, Object associated) {
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    PsiMember member = (PsiMember)element;
    if (!isInteresting(element))
      return true;
    if (myPsiClass.isInterface() && isObjectMember(element))
      return true;
    if (!myAllMembers.contains(member) && myFilter.isVisible(member, myPsiClass)) {
      if (member instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod)member;
        if (shouldAdd(psiMethod)) {
          mapMethodBySignature(psiMethod);
          myAllMembers.add(psiMethod);
        }
      } else myAllMembers.add(member);
    }
    return true;
  }

  private boolean isObjectMember(PsiElement element) {
    if (!(element instanceof PsiMethod))
      return false;
    return ((PsiMethod) element).getContainingClass().getQualifiedName().equals(Object.class.getName());
  }

  private void mapMethodBySignature(PsiMethod psiMethod) {
    myMethodsBySignature.put(psiMethod.getSignature(PsiSubstitutor.EMPTY), psiMethod);
  }

  private boolean shouldAdd(PsiMethod psiMethod) {
    MethodSignature signature = psiMethod.getSignature(PsiSubstitutor.EMPTY);
    PsiMethod  previousMethod = (PsiMethod) myMethodsBySignature.get(signature);
    if (previousMethod == null)
      return true;
    if (isInheritor(psiMethod, previousMethod)) {
      myAllMembers.remove(previousMethod);
      return true;
    }
    return false;
  }

  private boolean isInteresting(PsiElement element) {
    return element instanceof PsiMethod ||
            element instanceof PsiField ||
            element instanceof PsiClass;
  }

  public static boolean isInheritor(PsiMethod method, PsiMethod baseMethod) {
    if (isStatic(method) || isStatic(baseMethod))
      return false;
    return method.getContainingClass().isInheritor(baseMethod.getContainingClass(), true);
  }

  public static boolean isStatic(PsiMethod method) {
    return method.hasModifierProperty(PsiModifier.STATIC);
  }

  public static abstract class MemberFilter {
    public boolean isVisible(PsiMember element, PsiClass psiClass) {
      if (isInheritedConstructor(element, psiClass))
        return false;
      if (!PsiUtil.isAccessible(element, psiClass, null))
        return false;

      return isVisible(element);
    }

    public Condition<PsiMember> visibleInClass(final PsiClass psiClass) {
      return new Condition<PsiMember>() {
        public boolean value(PsiMember psiElement) {
          return isVisible(psiElement, psiClass);
        }
      };
    }

    private boolean isInheritedConstructor(PsiMember member, PsiClass psiClass) {
      if (!(member instanceof PsiMethod))
        return false;
      PsiMethod method = (PsiMethod)member;
      return method.isConstructor() && method.getContainingClass() != psiClass;
    }

    public ArrayList<PsiElement> copyVisible(List<PsiElement> list) {
      ArrayList<PsiElement> result = new ArrayList<PsiElement>();
      for (Iterator<PsiElement> iterator = list.iterator(); iterator.hasNext();) {
        PsiElement psiElement = iterator.next();
        if (psiElement instanceof PsiModifierListOwner && !isVisible((PsiModifierListOwner) psiElement))
          continue;
        result.add(psiElement);
      }
      return result;
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
