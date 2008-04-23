package com.intellij.psi.impl;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiSuperMethodImplUtil {
  private static final Key<CachedValue<Map<MethodSignature, HierarchicalMethodSignature>>> SIGNATURES_KEY = Key.create("MAP_KEY");

  private PsiSuperMethodImplUtil() {
  }

  @NotNull
  public static PsiMethod[] findSuperMethods(PsiMethod method) {
    return findSuperMethods(method, null);
  }

  @NotNull
  public static PsiMethod[] findSuperMethods(PsiMethod method, boolean checkAccess) {
    if (!canHaveSuperMethod(method, checkAccess, false)) return PsiMethod.EMPTY_ARRAY;
    return findSuperMethodsInternal(method, null);
  }

  @NotNull
  public static PsiMethod[] findSuperMethods(PsiMethod method, PsiClass parentClass) {
    if (!canHaveSuperMethod(method, true, false)) return PsiMethod.EMPTY_ARRAY;
    return findSuperMethodsInternal(method, parentClass);
  }


  @NotNull
  private static PsiMethod[] findSuperMethodsInternal(PsiMethod method, PsiClass parentClass) {
    List<MethodSignatureBackedByPsiMethod> outputMethods = findSuperMethodSignatures(method, parentClass, false);

    return MethodSignatureUtil.convertMethodSignaturesToMethods(outputMethods);
  }

  @NotNull
  public static List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(PsiMethod method,
                                                                                                         boolean checkAccess) {
    if (!canHaveSuperMethod(method, checkAccess, true)) return Collections.emptyList();
    return findSuperMethodSignatures(method, null, true);
  }

  @NotNull
  private static List<MethodSignatureBackedByPsiMethod> findSuperMethodSignatures(PsiMethod method,
                                                                                           PsiClass parentClass,
                                                                                           boolean allowStaticMethod) {

    return new ArrayList<MethodSignatureBackedByPsiMethod>(SuperMethodsSearch.search(method, parentClass, true, allowStaticMethod).findAll());
  }

  private static boolean canHaveSuperMethod(PsiMethod method, boolean checkAccess, boolean allowStaticMethod) {
    if (method.isConstructor()) return false;
    if (!allowStaticMethod && method.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (checkAccess && method.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    PsiClass parentClass = method.getContainingClass();
    return parentClass != null && !"java.lang.Object".equals(parentClass.getQualifiedName());
  }

  @Nullable
  public static PsiMethod findDeepestSuperMethod(PsiMethod method) {
    if (!canHaveSuperMethod(method, true, false)) return null;
    return DeepestSuperMethodsSearch.search(method).findFirst();
  }

  public static PsiMethod[] findDeepestSuperMethods(PsiMethod method) {
    if (!canHaveSuperMethod(method, true, false)) return PsiMethod.EMPTY_ARRAY;
    Collection<PsiMethod> collection = DeepestSuperMethodsSearch.search(method).findAll();
    return collection.toArray(new PsiMethod[collection.size()]);
  }

  private static Map<MethodSignature, HierarchicalMethodSignature> buildMethodHierarchy(PsiClass aClass,
                                                                                        PsiSubstitutor substitutor,
                                                                                        final boolean includePrivates,
                                                                                        final Set<PsiClass> visited,
                                                                                        boolean isInRawContext) {
    Map<MethodSignature, HierarchicalMethodSignature> result = new LinkedHashMap<MethodSignature, HierarchicalMethodSignature>();
    final Map<MethodSignature, List<PsiMethod>> sameParameterErasureMethods = new THashMap<MethodSignature, List<PsiMethod>>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);

    Map<MethodSignature, HierarchicalMethodSignatureImpl> map = new THashMap<MethodSignature, HierarchicalMethodSignatureImpl>(new TObjectHashingStrategy<MethodSignature>() {
      public int computeHashCode(MethodSignature object) {
        return MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY.computeHashCode(object);
      }

      public boolean equals(MethodSignature o1, MethodSignature o2) {
        if (!MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY.equals(o1, o2)) return false;
        List<PsiMethod> list = sameParameterErasureMethods.get(o1);
        boolean toCheckReturnType = list != null && list.size() > 1;
        if (toCheckReturnType) {
          PsiType returnType1 = ((MethodSignatureBackedByPsiMethod)o1).getMethod().getReturnType();
          PsiType returnType2 = ((MethodSignatureBackedByPsiMethod)o2).getMethod().getReturnType();
          if (returnType1 == null && returnType2 == null) return true;
          if (returnType1 == null || returnType2 == null) return false;
          final PsiType type1 = returnType1 instanceof PsiPrimitiveType ? returnType1 : PsiType.VOID;
          final PsiType type2 = returnType2 instanceof PsiPrimitiveType ? returnType2 : PsiType.VOID;
          return type1.equals(type2);
        }
        else {
          return true;
        }
      }
    });

    for (PsiMethod method : aClass.getMethods()) {
      if (!includePrivates && method.hasModifierProperty(PsiModifier.PRIVATE)) continue;
      final MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, substitutor, isInRawContext);
      HierarchicalMethodSignatureImpl newH = new HierarchicalMethodSignatureImpl(signature);

      List<PsiMethod> list = sameParameterErasureMethods.get(signature);
      if (list == null) {
        list = new SmartList<PsiMethod>();
        sameParameterErasureMethods.put(signature, list);
      }
      list.add(method);

      result.put(signature, newH);
      map.put(signature, newH);
    }

    for (PsiClassType superType : aClass.getSuperTypes()) {
      PsiClassType.ClassResolveResult superTypeResolveResult = superType.resolveGenerics();
      PsiClass superClass = superTypeResolveResult.getElement();
      if (superClass == null) continue;
      if (!visited.add(superClass)) continue; // cyclic inheritance
      final PsiSubstitutor superSubstitutor = superTypeResolveResult.getSubstitutor();
      PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(superClass, superSubstitutor, substitutor);

      final boolean isInRawContextSuper = isInRawContext || PsiUtil.isRawSubstitutor(superClass, superSubstitutor);
      Map<MethodSignature, HierarchicalMethodSignature> superResult = buildMethodHierarchy(superClass, finalSubstitutor, false, visited, isInRawContextSuper);
      visited.remove(superClass);

      for (MethodSignature superSignature : superResult.keySet()) {
        HierarchicalMethodSignature hierarchicalMethodSignature = superResult.get(superSignature);
        if (!PsiUtil.isAccessible(hierarchicalMethodSignature.getMethod(), aClass, aClass)) continue;
        HierarchicalMethodSignatureImpl existing = map.get(superSignature);
        if (existing == null) {
          map.put(superSignature, copy(hierarchicalMethodSignature));
        }
        else if (isSuperMethod(aClass, existing, hierarchicalMethodSignature)) {
          mergeSupers(existing, hierarchicalMethodSignature);
        }                                                                 
        // just drop an invalid method declaration there - to highlight accordingly
        else if (!result.containsKey(superSignature)) {
          result.put(superSignature, hierarchicalMethodSignature);
        }
      }
    }


    for (MethodSignature methodSignature : map.keySet()) {
      HierarchicalMethodSignatureImpl hierarchicalMethodSignature = map.get(methodSignature);
      if (result.get(methodSignature) == null && PsiUtil.isAccessible(hierarchicalMethodSignature.getMethod(), aClass, aClass)) {
        result.put(methodSignature, hierarchicalMethodSignature);
      }
    }

    return result;
  }

  private static void mergeSupers(final HierarchicalMethodSignatureImpl existing, final HierarchicalMethodSignature superSignature) {
    for (HierarchicalMethodSignature existingSuper : existing.getSuperSignatures()) {
      if (existingSuper.getMethod() == superSignature.getMethod()) {
        for (HierarchicalMethodSignature signature : superSignature.getSuperSignatures()) {
          mergeSupers((HierarchicalMethodSignatureImpl)existingSuper, signature);
        }
        return;
      }
    }
    if (existing.getMethod() == superSignature.getMethod()) {
      List<HierarchicalMethodSignature> existingSupers = existing.getSuperSignatures();
      for (HierarchicalMethodSignature supers : superSignature.getSuperSignatures()) {
        if (!existingSupers.contains(supers)) existing.addSuperSignature(copy(supers));
      }
    }
    else {
      HierarchicalMethodSignatureImpl copy = copy(superSignature);
      existing.addSuperSignature(copy);
    }
  }

  private static boolean isSuperMethod(PsiClass aClass, HierarchicalMethodSignatureImpl hierarchicalMethodSignature,
                                       HierarchicalMethodSignature superSignatureHierarchical) {
    PsiMethod superMethod = superSignatureHierarchical.getMethod();
    PsiClass superClass = superMethod.getContainingClass();
    return !superMethod.isConstructor()
           && !aClass.equals(superClass)
           && PsiUtil.isAccessible(superMethod, aClass, aClass)
           && MethodSignatureUtil.isSubsignature(superSignatureHierarchical, hierarchicalMethodSignature);
  }

  private static HierarchicalMethodSignatureImpl copy(HierarchicalMethodSignature hi) {
    HierarchicalMethodSignatureImpl hierarchicalMethodSignature = new HierarchicalMethodSignatureImpl(hi);
    for (HierarchicalMethodSignature his : hi.getSuperSignatures()) {
      hierarchicalMethodSignature.addSuperSignature(copy(his));
    }
    return hierarchicalMethodSignature;
  }

  private static PsiSubstitutor obtainFinalSubstitutor(PsiClass superClass,
                                                       PsiSubstitutor superSubstitutor,
                                                       PsiSubstitutor derivedSubstitutor) {
    Iterator<PsiTypeParameter> superTypeParams = PsiUtil.typeParametersIterator(superClass);
    if (!superTypeParams.hasNext()) return PsiSubstitutor.EMPTY;

    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(superClass.getProject()).getElementFactory();

    final Map<PsiTypeParameter, PsiType> map = new HashMap<PsiTypeParameter, PsiType>();
    while (superTypeParams.hasNext()) {
      PsiTypeParameter typeParameter = superTypeParams.next();
      PsiType type = superSubstitutor.substitute(typeParameter);
      final PsiType t = derivedSubstitutor.substitute(type);
      map.put(typeParameter, t);
    }

    return elementFactory.createSubstitutor(map);
  }

  public static Collection<HierarchicalMethodSignature> getVisibleSignatures(PsiClass aClass) {
    Map<MethodSignature, HierarchicalMethodSignature> map = getSignaturesMap(aClass);
    return map.values();
  }

  @NotNull public static HierarchicalMethodSignature getHierarchicalMethodSignature(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    HierarchicalMethodSignature result = null;
    if (aClass != null) {
      result = getSignaturesMap(aClass).get(method.getSignature(PsiSubstitutor.EMPTY));
    }
    if (result == null) {
      result = new HierarchicalMethodSignatureImpl((MethodSignatureBackedByPsiMethod)method.getSignature(PsiSubstitutor.EMPTY));
    }
    return result;
  }

  private static Map<MethodSignature, HierarchicalMethodSignature> getSignaturesMap(final PsiClass aClass) {
    CachedValue<Map<MethodSignature, HierarchicalMethodSignature>> value = aClass.getUserData(SIGNATURES_KEY);
    if (value == null) {
      BySignaturesCachedValueProvider provider = new BySignaturesCachedValueProvider(aClass);
      value = aClass.getManager().getCachedValuesManager().createCachedValue(provider, false);
      //Do not cache for nonphysical elements
      if (aClass.isPhysical()) {
        UserDataHolderEx dataHolder = (UserDataHolderEx)aClass;
        value = dataHolder.putUserDataIfAbsent(SIGNATURES_KEY, value);
      }
    }

    return value.getValue();
  }

  private static class BySignaturesCachedValueProvider implements CachedValueProvider<Map<MethodSignature, HierarchicalMethodSignature>> {
    private final PsiClass myClass;

    public BySignaturesCachedValueProvider(final PsiClass aClass) {
      myClass = aClass;
    }

    public Result<Map<MethodSignature, HierarchicalMethodSignature>> compute() {
      Map<MethodSignature, HierarchicalMethodSignature> result = buildMethodHierarchy(myClass, PsiSubstitutor.EMPTY, true, new THashSet<PsiClass>(), false);
      assert result != null;


      return new Result<Map<MethodSignature, HierarchicalMethodSignature>>(result, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }
  }
}
