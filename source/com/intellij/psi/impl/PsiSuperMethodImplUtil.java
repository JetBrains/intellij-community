package com.intellij.psi.impl;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PsiSuperMethodImplUtil {
  private static final Key<CachedValue<Map<MethodSignature, HierarchicalMethodSignatureImpl>>> SIGNATURES_KEY = Key.create("MAP_KEY");

  private PsiSuperMethodImplUtil() {
  }

  public static @NotNull PsiMethod[] findSuperMethods(PsiMethod method) {
    return findSuperMethods(method, null);
  }

  public static @NotNull PsiMethod[] findSuperMethods(PsiMethod method, boolean checkAccess) {
    if (!canHaveSuperMethod(method, checkAccess, false)) return PsiMethod.EMPTY_ARRAY;
    return findSuperMethodsInternal(method, null);
  }

  public static @NotNull PsiMethod[] findSuperMethods(PsiMethod method, PsiClass parentClass) {
    if (!canHaveSuperMethod(method, true, false)) return PsiMethod.EMPTY_ARRAY;
    return findSuperMethodsInternal(method, parentClass);
  }


  private static @NotNull PsiMethod[] findSuperMethodsInternal(PsiMethod method, PsiClass parentClass) {
    List<MethodSignatureBackedByPsiMethod> outputMethods = findSuperMethodSignatures(method, parentClass, false);

    return MethodSignatureUtil.convertMethodSignaturesToMethods(outputMethods);
  }

  public static @NotNull List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(PsiMethod method,
                                                                                                         boolean checkAccess) {
    if (!canHaveSuperMethod(method, checkAccess, true)) return Collections.emptyList();
    return findSuperMethodSignatures(method, null, true);
  }

  private static @NotNull List<MethodSignatureBackedByPsiMethod> findSuperMethodSignatures(PsiMethod method,
                                                                                           PsiClass parentClass,
                                                                                           boolean allowStaticMethod) {

    return new ArrayList<MethodSignatureBackedByPsiMethod>(SuperMethodsSearch.search(method, parentClass, true, allowStaticMethod).findAll());
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
    return DeepestSuperMethodsSearch.search(method).findFirst();
  }

  public static PsiMethod[] findDeepestSuperMethods(PsiMethod method) {
    if (!canHaveSuperMethod(method, true, false)) return PsiMethod.EMPTY_ARRAY;
    Collection<PsiMethod> collection = DeepestSuperMethodsSearch.search(method).findAll();
    return collection.toArray(new PsiMethod[collection.size()]);
  }

  private static void buildMethodHierarchy(PsiClass aClass,
                                           PsiSubstitutor substitutor,
                                           Set<PsiClass> visited,
                                           Map<MethodSignature, HierarchicalMethodSignatureImpl> signatures,
                                           Map<MethodSignature, HierarchicalMethodSignatureImpl> result
  ) {
    if (visited.contains(aClass)) return;
    visited.add(aClass);
    final PsiMethod[] methods = aClass.getMethods();
    Map<MethodSignature, HierarchicalMethodSignatureImpl> toRestore = new THashMap<MethodSignature, HierarchicalMethodSignatureImpl>();
    for (PsiMethod method : methods) {
      final MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, substitutor);
      final HierarchicalMethodSignatureImpl signatureHierarchical = new HierarchicalMethodSignatureImpl(signature);
      final HierarchicalMethodSignatureImpl existing = signatures.get(signature);
      if (existing != null &&
          !existing.getMethod().isConstructor() &&
          !aClass.equals(existing.getMethod().getContainingClass())) {
        existing.addSuperSignature(signatureHierarchical);
        toRestore.put(signature, existing);
      }
      else {
        result.put(signature, signatureHierarchical);
      }
      signatures.put(signature, signatureHierarchical);
    }

    final PsiClassType[] superTypes = aClass.getSuperTypes(); //it is essential that extends list types go before implements list types
    for (final PsiClassType superType : superTypes) {
      final PsiClassType.ClassResolveResult superTypeResolveResult = superType.resolveGenerics();
      PsiClass superClass = superTypeResolveResult.getElement();
      if (superClass == null) continue;
      PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(superClass, superTypeResolveResult.getSubstitutor(), substitutor,
                                                                                aClass);
      buildMethodHierarchy(superClass, finalSubstitutor, visited, signatures, result);
    }

    signatures.putAll(toRestore);
  }

  private static PsiSubstitutor obtainFinalSubstitutor(PsiClass candidateClass,
                                               PsiSubstitutor candidateSubstitutor,
                                               PsiSubstitutor substitutor,
                                               final PsiElement place) {
    PsiElementFactory elementFactory = candidateClass.getManager().getElementFactory();
    final PsiType containingType = elementFactory.createType(candidateClass, candidateSubstitutor, PsiUtil.getLanguageLevel(place));
    PsiType type = substitutor.substitute(containingType);
    if (!(type instanceof PsiClassType)) return candidateSubstitutor;
    return ((PsiClassType)type).resolveGenerics().getSubstitutor();
  }

  public static Collection<HierarchicalMethodSignature> getVisibleSignatures(PsiClass aClass) {
    Map<MethodSignature, HierarchicalMethodSignatureImpl> map = getSignaturesMap(aClass);
    return (Collection)map.values();
  }

  @NotNull public static HierarchicalMethodSignature getHierarchicalMethodSignature(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    HierarchicalMethodSignatureImpl result = null;
    if (aClass != null) {
      result = getSignaturesMap(aClass).get(method.getSignature(PsiSubstitutor.EMPTY));
    }
    if (result == null) {
      result = new HierarchicalMethodSignatureImpl((MethodSignatureBackedByPsiMethod)method.getSignature(PsiSubstitutor.EMPTY));
    }
    return result;
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
      buildMethodHierarchy(myClass, PsiSubstitutor.EMPTY, new HashSet<PsiClass>(), map, result
      );
      return new Result<Map<MethodSignature, HierarchicalMethodSignatureImpl>>
        (result, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }
  }
}
