package com.intellij.psi.impl;

import com.intellij.aspects.psi.PsiAspect;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
import com.intellij.j2ee.ejb.role.EjbImplMethodRole;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public class PsiSuperMethodImplUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.util.PsiSuperMethodImplUtil");

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
    final MethodSignatureUtil.MethodSignatureToMethods allMethodsCollection = MethodSignatureUtil.getSameSignatureMethods(parentClass);
    final MethodSignature originalMethodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    final List<MethodSignatureBackedByPsiMethod> methods = allMethodsCollection.get(originalMethodSignature);
    List<MethodSignatureBackedByPsiMethod> sameSignatureMethods = new ArrayList<MethodSignatureBackedByPsiMethod>();
    if (methods != null) {
      sameSignatureMethods.addAll(methods);
    }
    final EjbMethodRole role = J2EERolesUtil.getEjbRole(method);
    if (role instanceof EjbImplMethodRole) {
      final PsiMethod[] ejbDeclarations = EjbUtil.findEjbDeclarations(method);
      for (int i = 0; i < ejbDeclarations.length; i++) {
        PsiMethod ejbDeclaration = ejbDeclarations[i];
        sameSignatureMethods.add(new MethodSignatureBackedByPsiMethod(ejbDeclaration, PsiSubstitutor.EMPTY));
      }
    }
    PsiManager manager = method.getManager();

    List<MethodSignatureBackedByPsiMethod> outputMethods = new ArrayList<MethodSignatureBackedByPsiMethod>();
    AllMethodsLoop:
    for (int i = 0; i < sameSignatureMethods.size(); i++) {
      final MethodSignatureBackedByPsiMethod methodSignature = sameSignatureMethods.get(i);
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

  public static PsiMethod findConstructorInSuper(PsiMethod constructor) {
    if (constructor.getBody() != null) {
      PsiStatement[] statements = constructor.getBody().getStatements();
      if (statements.length > 0) {
        PsiElement firstChild = statements[0].getFirstChild();
        if (firstChild instanceof PsiMethodCallExpression) {
          PsiReferenceExpression superExpr = ((PsiMethodCallExpression)firstChild).getMethodExpression();
          if (superExpr.getText().equals("super")) {
            PsiElement superConstructor = superExpr.resolve();
            if (superConstructor instanceof PsiMethod) {
              return (PsiMethod)superConstructor;
            }
          }
        }
      }
    }

    PsiClass containingClass = constructor.getContainingClass();
    if (containingClass != null) {
      PsiClass superClass = containingClass.getSuperClass();
      if (superClass != null) {
        PsiMethod defConstructor = constructor.getManager().getElementFactory().createConstructor();
        try {
          defConstructor.getNameIdentifier().replace(superClass.getNameIdentifier().copy());
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
          return null;
        }
        return superClass.findMethodBySignature(defConstructor, false);
      }
    }
    return null;
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
    for (int i = 0; i < allMethods.length; i++) {
      PsiMethod superMethod = allMethods[i];
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

  /**
   * @return all overridden methods sorted by hierarchy,
   *         i.e  Map: PsiMethod method -> List of overridden methods (access control rules are respected)
   */
  public static Map getMethodHierarchy(PsiMethod method) {
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    return getMethodHierarchy(method, aClass);
  }

  public static Map getMethodHierarchy(PsiMethod method, PsiClass aClass) {
    Map map = new HashMap();
    List allMethods = new ArrayList();
    getMethodHierarchy(method, aClass, map, allMethods);
    return map;
  }

  public static Map getMethodHierarchy(MethodSignature method, PsiClass containingClass) {
    final Map map = new HashMap();
    final List allMethods = new ArrayList();
    getMethodHierarchy(method, containingClass, map, allMethods);
    return map;
  }

  private static void getMethodHierarchy(Object method, PsiClass aClass, Map map, List allMethods) {
    final PsiClass[] superTypes = aClass.getSupers();
    final int startMethodIndex = allMethods.size();
    for (int i = 0; i < superTypes.length; i++) {
      PsiClass superType = superTypes[i];
      final PsiMethod superMethod;
      if (method instanceof PsiMethod) {
        superMethod = MethodSignatureUtil.findMethodBySignature(superType, (PsiMethod)method, false);
      }
      else {
        superMethod = MethodSignatureUtil.findMethodBySignature(superType, (MethodSignature)method, false);
      }
      if (superMethod == null) {
        getMethodHierarchy(method, superType, map, allMethods);
      }
      else {
        if (PsiUtil.isAccessible(superMethod, aClass, aClass)) {
          allMethods.add(superMethod);
        }
      }
    }
    final int endMethodIndex = allMethods.size();
    map.put(method, new ArrayList(allMethods.subList(startMethodIndex, endMethodIndex)));
    for (int i = startMethodIndex; i < endMethodIndex; i++) {
      final PsiMethod superMethod = (PsiMethod)allMethods.get(i);
      if (map.get(superMethod) == null) {
        getMethodHierarchy(superMethod, superMethod.getContainingClass(), map, allMethods);
      }
    }
  }

  // remove from list all methods overridden by contextClass or its super classes
  // if (checkForSiblingOverride) then abstract method inherited from superClass1 and implemented in superClass2 considered to be overridden by contextClass
  public static void removeOverriddenMethods(List<MethodSignatureBackedByPsiMethod> sameSignatureMethods,
                                             PsiClass contextClass,
                                             boolean checkForSiblingOverride) {
    for (int i = sameSignatureMethods.size() - 1; i >= 0; i--) {
      final MethodSignatureBackedByPsiMethod methodBackedMethodSignature1 = sameSignatureMethods.get(i);
      PsiMethod method1 = methodBackedMethodSignature1.getMethod();
      final PsiClass class1 = method1.getContainingClass();
      if (method1.hasModifierProperty(PsiModifier.STATIC)
          || method1.hasModifierProperty(PsiModifier.PRIVATE)) {
        continue;
      }
      // check if method1 is overridden
      boolean overridden = false;
      for (int j = 0; j < sameSignatureMethods.size(); j++) {
        if (i == j) continue;
        final MethodSignatureBackedByPsiMethod methodBackedMethodSignature2 = sameSignatureMethods.get(j);
        PsiMethod method2 = methodBackedMethodSignature2.getMethod();
        final PsiClass class2 = method2.getContainingClass();
        if (InheritanceUtil.isInheritorOrSelf(class2, class1, true)
            // method from interface cannot override method from Object
            && !("java.lang.Object".equals(class1.getQualifiedName()) && class2.isInterface())
            && !(method1.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && !method1.getManager().arePackagesTheSame(class1, class2))) {
          overridden = true;
          break;
        }
        // check for sibling override: class Context extends Implementations implements Declarations {}
        // see JLS 8.4.6.4
        if (checkForSiblingOverride
            && !method2.hasModifierProperty(PsiModifier.ABSTRACT)
            && PsiUtil.isAccessible(method1, contextClass, contextClass)
            && PsiUtil.isAccessible(method2, contextClass, contextClass)) {
          overridden = true;
          break;
        }
      }
      if (overridden) {
        sameSignatureMethods.remove(i);
      }
    }
  }

}
