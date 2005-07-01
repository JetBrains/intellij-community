package com.intellij.psi.impl;

import com.intellij.aspects.psi.PsiAspect;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.j2ee.ejb.role.EjbImplMethodRole;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
import com.intellij.psi.*;
import com.intellij.psi.util.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PsiSuperMethodImplUtil {

  public static PsiPointcutDef findSuperPointcut(PsiPointcutDef pointcut) {
    return findSuperPointcut(pointcut, pointcut.getContainingAspect());
  }

  private static PsiPointcutDef findSuperPointcut(PsiPointcutDef pointcut, PsiAspect psiAspect) {
    PsiClass superClass = psiAspect.getSuperClass();

    while (!(superClass instanceof PsiAspect) && superClass != null) superClass = superClass.getSuperClass();
    if (superClass == null) return null;

    PsiAspect superAspect = (PsiAspect)superClass;
    return superAspect.findPointcutDefBySignature(pointcut, true);
  }

  public static PsiPointcutDef findDeepestSuperPointcut(PsiPointcutDef pointcut) {
    PsiPointcutDef superPointcut = findSuperPointcut(pointcut);
    PsiPointcutDef prevSuperPointcut = null;

    while (superPointcut != null) {
      prevSuperPointcut = superPointcut;
      superPointcut = findSuperPointcut(prevSuperPointcut);
    }

    return prevSuperPointcut;
  }

  public static PsiMethod[] findSuperMethods(PsiMethod method) {
    return findSuperMethods(method, method.getContainingClass());
  }

  public static PsiMethod[] findSuperMethods(PsiMethod method, boolean checkAccess) {
    if (!canHaveSuperMethod(method, checkAccess, false)) return PsiMethod.EMPTY_ARRAY;
    final PsiClass aClass = method.getContainingClass();
    return findSuperMethodsInternal(method, aClass);
  }

  public static PsiMethod[] findSuperMethods(PsiMethod method, PsiClass parentClass) {
    if (!canHaveSuperMethod(method, true, false)) return PsiMethod.EMPTY_ARRAY;
    return findSuperMethodsInternal(method, parentClass);
  }


  private static PsiMethod[] findSuperMethodsInternal(PsiMethod method, PsiClass parentClass) {
    List<MethodSignatureBackedByPsiMethod> outputMethods = findSuperMethodSignatures(method, parentClass, false);

    return MethodSignatureUtil.convertMethodSignaturesToMethods(outputMethods);
  }

  public static List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(PsiMethod method,
                                                                                                boolean checkAccess) {
    if (!canHaveSuperMethod(method, checkAccess, true)) return Collections.EMPTY_LIST;
    return findSuperMethodSignatures(method, method.getContainingClass(), true);
  }

  private static List<MethodSignatureBackedByPsiMethod> findSuperMethodSignatures(PsiMethod method,
                                                                                  PsiClass parentClass, boolean allowStaticMethod) {
    final boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    final MethodSignatureUtil.MethodSignatureToMethods allMethodsCollection = MethodSignatureUtil.getOverrideEquivalentMethods(parentClass);
    final MethodSignature originalMethodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    final List<MethodSignatureBackedByPsiMethod> methods = allMethodsCollection.get(originalMethodSignature);
    List<MethodSignatureBackedByPsiMethod> sameSignatureMethods = new ArrayList<MethodSignatureBackedByPsiMethod>();
    if (methods != null) {
      sameSignatureMethods.addAll(methods);
    }
    final EjbMethodRole role = J2EERolesUtil.getEjbRole(method);
    if (role instanceof EjbImplMethodRole) {
      final PsiMethod[] ejbDeclarations = EjbUtil.findEjbDeclarations(method);
      for (PsiMethod ejbDeclaration : ejbDeclarations) {
        sameSignatureMethods.add(MethodSignatureBackedByPsiMethod.create(ejbDeclaration, PsiSubstitutor.EMPTY));
      }
    }
    PsiManager manager = method.getManager();

    List<MethodSignatureBackedByPsiMethod> outputMethods = new ArrayList<MethodSignatureBackedByPsiMethod>();
    AllMethodsLoop:
    for (final MethodSignatureBackedByPsiMethod methodSignature : sameSignatureMethods) {
      PsiMethod superMethod = methodSignature.getMethod();
      PsiClass superClass = superMethod.getContainingClass();
      if (superClass == null || manager.areElementsEquivalent(superClass, parentClass)) continue;
      final boolean isSuperStatic = superMethod.hasModifierProperty(PsiModifier.STATIC);
      if (isStatic != isSuperStatic) continue;
      if (!allowStaticMethod && isSuperStatic) continue;
      if (superMethod.hasModifierProperty(PsiModifier.PRIVATE)) continue;
      // cannot override package local method from other package
      if (superMethod.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
          && !manager.arePackagesTheSame(parentClass, superClass)) {
        continue;
      }

      for (int j = 0; j < outputMethods.size(); j++) {
        final MethodSignatureBackedByPsiMethod methodSignature1 = outputMethods.get(j);
        PsiMethod superMethod1 = methodSignature1.getMethod();
        PsiClass superClass1 = superMethod1.getContainingClass();
        if (superClass1.isInheritor(superClass, true)) {
          continue AllMethodsLoop;
        }
        if (superClass.isInheritor(superClass1, true)) {
          outputMethods.set(j, methodSignature);
          continue AllMethodsLoop;
        }
      }
      outputMethods.add(methodSignature);
    }
    return outputMethods;
  }

  private static boolean canHaveSuperMethod(PsiMethod method, boolean checkAccess, boolean allowStaticMethod) {
    if (method.isConstructor()) return false;
    if (!allowStaticMethod && method.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (checkAccess && method.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    PsiClass parentClass = method.getContainingClass();
    if (parentClass == null) return false;
    if ("java.lang.Object".equals(parentClass.getQualifiedName())) return false;
    return true;
  }

  public static PsiMethod findDeepestSuperMethod(PsiMethod method) {
    if (method.isConstructor()) return null;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return null;
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) return null;

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return null;
    }

    final PsiMethod[] allMethods;

    PsiMethod[] ejbDeclarations = EjbUtil.findEjbDeclarations(method);
    boolean isEjbInherited = J2EERolesUtil.getEjbRole(method) instanceof EjbImplMethodRole && ejbDeclarations.length != 0;
    if (isEjbInherited) {
      allMethods = ejbDeclarations;
    }
    else {
      allMethods = aClass.getAllMethods();
    }
    PsiMethod topSuper = null;
    for (PsiMethod superMethod : allMethods) {
      PsiClass superClass = superMethod.getContainingClass();
      if (superClass.equals(aClass)) continue;
      PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
      if (superClassSubstitutor == null) superClassSubstitutor = PsiSubstitutor.EMPTY;
      boolean looksLikeSuperMethod = method.getName().equals(superMethod.getName()) &&
                                     !superMethod.hasModifierProperty(PsiModifier.STATIC) &&
                                     PsiUtil.isAccessible(superMethod, aClass, aClass) &&
                                     method.getSignature(PsiSubstitutor.EMPTY).equals(superMethod.getSignature(superClassSubstitutor));
      if (isEjbInherited || looksLikeSuperMethod) {
        if (topSuper != null && superClass.isInheritor(topSuper.getContainingClass(), true)) {
          continue;
        }
        topSuper = superMethod;
      }
    }
    return topSuper;
  }
}
