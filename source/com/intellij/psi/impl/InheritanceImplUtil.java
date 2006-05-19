package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.HashSet;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

public class InheritanceImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.InheritanceImplUtil");

  public static boolean isInheritor(PsiClass candidateClass, final PsiClass baseClass, final boolean checkDeep) {
    if (baseClass instanceof PsiAnonymousClass) {
      return false;
    }
    return isInheritor(candidateClass, baseClass, checkDeep, null);
  }

  private static boolean isInheritor(PsiClass candidateClass, PsiClass baseClass, boolean checkDeep, Set<PsiClass> checkedClasses) {
    if (candidateClass instanceof PsiAnonymousClass) {
      final PsiClass baseCandidateClass = ((PsiAnonymousClass)candidateClass).getBaseClassType().resolve();
      if (baseCandidateClass == null) return false;
      return InheritanceUtil.isInheritorOrSelf(baseCandidateClass, baseClass, checkDeep);
    }
    PsiManager manager = candidateClass.getManager();
    /* //TODO fix classhashprovider so it doesn't use class qnames only
    final ClassHashProvider provider = getHashProvider((PsiManagerImpl) manager);
    if (checkDeep && provider != null) {
      try {
        return provider.isInheritor(baseClass, candidateClass);
      }
      catch (ClassHashProvider.OutOfRangeException e) {
      }
    }
    */
    if(checkDeep && LOG.isDebugEnabled()){
      LOG.debug("Using uncached version for " + candidateClass.getQualifiedName() + " and " + baseClass);
    }
    PsiClass objectClass = manager.findClass("java.lang.Object", candidateClass.getResolveScope());
    if (manager.areElementsEquivalent(baseClass, objectClass)) {
      if (manager.areElementsEquivalent(candidateClass, objectClass)) return false;
      if (checkDeep || candidateClass.isInterface()) return true;
      return candidateClass.getExtendsListTypes().length == 0;
    }

    return isInheritorWithoutCaching(candidateClass, baseClass, checkDeep, checkedClasses);
  }

  private static boolean isInheritorWithoutCaching(PsiClass aClass, PsiClass baseClass, boolean checkDeep, Set<PsiClass> checkedClasses) {
    PsiManager manager = aClass.getManager();
    if (manager.areElementsEquivalent(aClass, baseClass)) return false;

    if (aClass.isInterface() && !baseClass.isInterface()) {
      return false;
    }

    //if (PsiUtil.hasModifierProperty(baseClass, PsiModifier.FINAL)) {
    //  return false;
    //}

    if (checkDeep) {
      if (checkedClasses == null) {
        checkedClasses = new HashSet<PsiClass>();
      }
      checkedClasses.add(aClass);
    }

    if (!aClass.isInterface() && baseClass.isInterface()) {
      if (checkDeep && checkInheritor(aClass.getSuperClass(), baseClass, checkDeep, checkedClasses)) {
        return true;
      }
      return checkInheritor(aClass.getInterfaces(), baseClass, checkDeep, checkedClasses);

    }
    else {
      return checkInheritor(aClass.getSupers(), baseClass, checkDeep, checkedClasses);
    }
  }

  private static boolean checkInheritor(PsiClass[] supers, PsiClass baseClass, boolean checkDeep, Set<PsiClass> checkedClasses) {
    for (PsiClass aSuper : supers) {
      if (checkInheritor(aSuper, baseClass, checkDeep, checkedClasses)) {
        return true;
      }
    }
    return false;
  }

  private static boolean checkInheritor(PsiClass aClass, PsiClass baseClass, boolean checkDeep, Set<PsiClass> checkedClasses) {
    if (aClass != null) {
      PsiManager manager = baseClass.getManager();
      if (manager.areElementsEquivalent(baseClass, aClass)) {
        return true;
      }
      if (checkedClasses != null && checkedClasses.contains(aClass)) { // to prevent infinite recursion
        return false;
      }
      if (checkDeep) {
        if (isInheritor(aClass, baseClass, checkDeep, checkedClasses)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isInheritorDeep(final PsiClass candidateClass, final PsiClass baseClass, @Nullable final PsiClass classToByPass) {
    if (baseClass instanceof PsiAnonymousClass) {
      return false;
    }

    Set<PsiClass> checkedClasses = null;
    if (classToByPass != null) {
      checkedClasses = new HashSet<PsiClass>();
      checkedClasses.add(classToByPass);
    }
    return isInheritor(candidateClass, baseClass, true, checkedClasses);
  }
}
