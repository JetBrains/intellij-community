package com.intellij.psi.impl;

import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.j2ee.ejb.role.EjbClassRole;
import com.intellij.j2ee.j2eeDom.J2EEElementsVisitor;
import com.intellij.j2ee.j2eeDom.ejb.Ejb;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.cache.impl.ClassHashProvider;
import com.intellij.psi.util.InheritanceUtil;

import java.util.ArrayList;
import java.util.List;

public class InheritanceImplUtil {
  private static final Key HASH_PROVIDER_KEY = Key.create("Hash provider for isInheritor");
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.InheritanceUtil");

  private static ClassHashProvider getHashProvider(final PsiManagerImpl manager) {
    final ClassHashProvider data = (ClassHashProvider) manager.getUserData(HASH_PROVIDER_KEY);
    if (data != null) return data;
    final ClassHashProvider newProvider = new ClassHashProvider(manager);
    manager.putUserData(HASH_PROVIDER_KEY, newProvider);
    return newProvider;
  }

  public static boolean isInheritor(PsiClass candidateClass, final PsiClass baseClass, final boolean checkDeep) {
    if (baseClass instanceof PsiAnonymousClass) {
      return false;
    }
    if (isInheritor(candidateClass, baseClass, checkDeep, null)) return true;

    final EjbClassRole classRole = J2EERolesUtil.getEjbRole(candidateClass);
    if (classRole != null && candidateClass.getManager().areElementsEquivalent(candidateClass, classRole.getEjb().getEjbClass().getPsiClass())) {
      final Ejb ejb = classRole.getEjb();
      return !EjbUtil.visitEjbInterfaces(ejb, new J2EEElementsVisitor(){
        public boolean visitInterface(PsiClass anInterface) {
          return !InheritanceUtil.isInheritorOrSelf(anInterface, baseClass, checkDeep);
        }
      });
    }
    return false;
  }

  private static boolean isInheritor(PsiClass candidateClass, PsiClass baseClass, boolean checkDeep, List<PsiClass> checkedClasses) {
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
      if (checkDeep) return true;
      if (candidateClass.isInterface()) return true;
      return candidateClass.getExtendsListTypes().length == 0;
    }

    return isInheritorWithoutCaching(candidateClass, baseClass, checkDeep, checkedClasses);
  }

  private static boolean isInheritorWithoutCaching(PsiClass aClass, PsiClass baseClass, boolean checkDeep, List<PsiClass> checkedClasses) {
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
        checkedClasses = new ArrayList<PsiClass>();
      }
      checkedClasses.add(aClass);
    }

    if (!aClass.isInterface() && baseClass.isInterface()) {
      if (checkDeep) {
        if (checkInheritor(aClass.getSuperClass(), baseClass, checkDeep, checkedClasses)) {
          return true;
        }
      }
      if (checkInheritor(aClass.getInterfaces(), baseClass, checkDeep, checkedClasses)) {
        return true;
      }

      return false;
    }
    else {
      if (checkInheritor(aClass.getSupers(), baseClass, checkDeep, checkedClasses)) {
        return true;
      }
      return false;
    }
  }

  private static boolean checkInheritor(PsiClass[] supers, PsiClass baseClass, boolean checkDeep, List<PsiClass> checkedClasses) {
    for (int i = 0; i < supers.length; i++) {
      if (checkInheritor(supers[i], baseClass, checkDeep, checkedClasses)) {
        return true;
      }
    }
    return false;
  }

  private static boolean checkInheritor(PsiClass aClass, PsiClass baseClass, boolean checkDeep, List<PsiClass> checkedClasses) {
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
}
