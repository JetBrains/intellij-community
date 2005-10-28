package com.intellij.psi.impl;

import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.j2ee.ejb.role.EjbImplMethodRole;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl;
import com.intellij.psi.util.*;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PsiSuperMethodImplUtil {
  private static final Key<CachedValue<Map<MethodSignature, HierarchicalMethodSignatureImpl>>> SIGNATURES_KEY = Key.create("MAP_KEY");
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiSuperMethodImplUtil");

  private PsiSuperMethodImplUtil() {
  }

  public static @NotNull PsiMethod[] findSuperMethods(PsiMethod method) {
    return findSuperMethods(method, method.getContainingClass());
  }

  public static @NotNull PsiMethod[] findSuperMethods(PsiMethod method, boolean checkAccess) {
    if (!canHaveSuperMethod(method, checkAccess, false)) return PsiMethod.EMPTY_ARRAY;
    final PsiClass aClass = method.getContainingClass();
    return findSuperMethodsInternal(method, aClass);
  }

  public static @NotNull PsiMethod[] findSuperMethods(PsiMethod method, PsiClass parentClass) {
    if (!canHaveSuperMethod(method, true, false)) return PsiMethod.EMPTY_ARRAY;
    return findSuperMethodsInternal(method, parentClass);
  }


  private static @NotNull PsiMethod[] findSuperMethodsInternal(PsiMethod method, PsiClass parentClass) {
    List<MethodSignatureBackedByPsiMethod> outputMethods = findSuperMethodSignatures(method, parentClass, false);

    return MethodSignatureUtil.convertMethodSignaturesToMethods(outputMethods);
  }

  @SuppressWarnings({"unchecked"})
  public static @NotNull List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(PsiMethod method,
                                                                                                         boolean checkAccess) {
    if (!canHaveSuperMethod(method, checkAccess, true)) return Collections.EMPTY_LIST;
    return findSuperMethodSignatures(method, method.getContainingClass(), true);
  }

  private static @NotNull List<MethodSignatureBackedByPsiMethod> findSuperMethodSignatures(PsiMethod method,
                                                                                           PsiClass parentClass, boolean allowStaticMethod) {
    final boolean myStatic = method.hasModifierProperty(PsiModifier.STATIC);
    List<MethodSignatureBackedByPsiMethod> result = new ArrayList<MethodSignatureBackedByPsiMethod>();

    final EjbMethodRole role = J2EERolesUtil.getEjbRole(method);
    if (role instanceof EjbImplMethodRole) {
      final PsiMethod[] ejbDeclarations = EjbUtil.findEjbDeclarations(method);
      for (PsiMethod ejbDeclaration : ejbDeclarations) {
        result.add(MethodSignatureBackedByPsiMethod.create(ejbDeclaration, PsiSubstitutor.EMPTY));
      }
    }

    Map<MethodSignature, HierarchicalMethodSignatureImpl> signaturesMap = getSignaturesMap(parentClass);
    HierarchicalMethodSignature signature = signaturesMap.get(method.getSignature(PsiSubstitutor.EMPTY));
    PsiResolveHelper helper = method.getManager().getResolveHelper();
    if (signature != null) {
      PsiMethod hisMethod = signature.getMethod();
      if (!parentClass.equals(hisMethod.getContainingClass())) {
        if (helper.isAccessible(hisMethod, method, null)) {
          boolean hisStatic = hisMethod.hasModifierProperty(PsiModifier.STATIC);
          if ((hisStatic == myStatic) && (allowStaticMethod || !hisStatic)) {
            result.add(signature);
          }
        }
      }
      else {
        List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();
        for (HierarchicalMethodSignature superSignature : superSignatures) {
          PsiMethod superMethod = superSignature.getMethod();
          boolean hisStatic = superMethod.hasModifierProperty(PsiModifier.STATIC);
          if (myStatic != hisStatic) continue;
          if (allowStaticMethod || !hisStatic) {
            if (helper.isAccessible(superMethod, method, null)) {
              result.add(superSignature);
            }
          }
        }
      }
    }

    return result;
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
    if (!canHaveSuperMethod(method, true, false)) return null;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    Map<MethodSignature, HierarchicalMethodSignatureImpl> signaturesMap = getSignaturesMap(aClass);
    HierarchicalMethodSignatureImpl hierarchical = signaturesMap.get(method.getSignature(PsiSubstitutor.EMPTY));
    LOG.assertTrue(hierarchical != null);
    HierarchicalMethodSignature deepest = findDeepestSuperOrSelfSignature(hierarchical);
    if (deepest == hierarchical) return null;
    if (deepest != null) return deepest.getMethod();

    PsiMethod[] ejbDeclarations = EjbUtil.findEjbDeclarations(method);
    if (ejbDeclarations.length == 1) return ejbDeclarations[0];

    return null;
  }

  private static HierarchicalMethodSignature findDeepestSuperOrSelfSignature(HierarchicalMethodSignature signature) {
    List<HierarchicalMethodSignature> supers = signature.getSuperSignatures();

    if (supers.size() == 1) return findDeepestSuperOrSelfSignature(supers.get(0));

    for (HierarchicalMethodSignature superSignature : supers) {
      PsiMethod superMethod = superSignature.getMethod();
      if (!superMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return findDeepestSuperOrSelfSignature(superSignature);
      }
    }
    return signature;
  }

  private static void buildMethodHierarchy(PsiClass aClass,
                                           PsiSubstitutor substitutor,
                                           Set<PsiClass> visited,
                                           Map<MethodSignature, HierarchicalMethodSignatureImpl> signatures,
                                           Map<MethodSignature, HierarchicalMethodSignatureImpl> result,
                                           final Map<MethodSignature, HierarchicalMethodSignatureImpl> toRestore) {
    if (visited.contains(aClass)) return;
    visited.add(aClass);
    final PsiMethod[] methods = aClass.getMethods();
    Map<MethodSignature, HierarchicalMethodSignatureImpl> ownToRestore = new HashMap<MethodSignature, HierarchicalMethodSignatureImpl>();
    for (PsiMethod method : methods) {
      final MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, substitutor);
      final HierarchicalMethodSignatureImpl signatureHierarchical = new HierarchicalMethodSignatureImpl(signature);
      final HierarchicalMethodSignatureImpl existing = signatures.get(signature);
      if (existing != null &&
          !existing.getMethod().isConstructor() &&
          !aClass.equals(existing.getMethod().getContainingClass())) {
        existing.addSuperSignature(signatureHierarchical);
        ownToRestore.put(signature, existing);
      } else {
        result.put(signature, signatureHierarchical);
        toRestore.put(signature, existing);
      }
      signatures.put(signature, signatureHierarchical);
    }

    final PsiClassType[] superTypes = aClass.getSuperTypes(); //it is essential that extends list types go before implements list types
    for (final PsiClassType superType : superTypes) {
      final PsiClassType.ClassResolveResult superTypeResolveResult = superType.resolveGenerics();
      PsiClass superClass = superTypeResolveResult.getElement();
      if (superClass == null) continue;
      PsiSubstitutor finalSubstitutor = PsiClassImplUtil.obtainFinalSubstitutor(superClass, superTypeResolveResult.getSubstitutor(), aClass, substitutor);
      buildMethodHierarchy(superClass, finalSubstitutor, visited, signatures, result, superClass.isInterface() ? ownToRestore : toRestore);
    }

    signatures.putAll(ownToRestore);
  }

  public static Collection<HierarchicalMethodSignature> getVisibleSignatures(PsiClass aClass) {
    Map<MethodSignature, HierarchicalMethodSignatureImpl> map = getSignaturesMap(aClass);
    return new LinkedHashSet<HierarchicalMethodSignature>(map.values());
  }

  private static Map<MethodSignature, HierarchicalMethodSignatureImpl> getSignaturesMap(final PsiClass aClass) {
    CachedValue<Map<MethodSignature, HierarchicalMethodSignatureImpl>> value = aClass.getUserData(SIGNATURES_KEY);
    if (value == null) {
      BySignaturesCachedValueProvider provider = new BySignaturesCachedValueProvider(aClass);
      value = aClass.getManager().getCachedValuesManager().createCachedValue(provider, false);
      //Do not cache for nonphysical elements
      if (aClass.isPhysical()) {
        aClass.putUserData(SIGNATURES_KEY, value);
      }
    }

    return value.getValue();
  }

  private static class BySignaturesCachedValueProvider implements CachedValueProvider<Map<MethodSignature, HierarchicalMethodSignatureImpl>> {
    private PsiClass myClass;

    public BySignaturesCachedValueProvider(final PsiClass aClass) {
      myClass = aClass;
    }

    public Result<Map<MethodSignature, HierarchicalMethodSignatureImpl>> compute() {
      final Map<MethodSignature, HierarchicalMethodSignatureImpl> map =
        new THashMap<MethodSignature, HierarchicalMethodSignatureImpl>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);
      Map<MethodSignature, HierarchicalMethodSignatureImpl> result = new LinkedHashMap<MethodSignature, HierarchicalMethodSignatureImpl>();
      buildMethodHierarchy(myClass, PsiSubstitutor.EMPTY, new HashSet<PsiClass>(), map, result,
                           new HashMap<MethodSignature, HierarchicalMethodSignatureImpl>());
      return new Result<Map<MethodSignature, HierarchicalMethodSignatureImpl>>
        (result, new Object[]{PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT});
    }
  }
}
