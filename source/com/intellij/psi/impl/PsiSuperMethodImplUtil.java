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

import java.util.*;

public class PsiSuperMethodImplUtil {
  private static final Key<CachedValue<Map<MethodSignature, HierarchicalMethodSignatureImpl>>> SIGNATURES_KEY = Key.create("MAP_KEY");

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

  public static PsiMethod findDeepestSuperMethod(PsiMethod method) {
    if (!canHaveSuperMethod(method, true, false)) return null;
    return DeepestSuperMethodsSearch.search(method).findFirst();
  }

  public static PsiMethod[] findDeepestSuperMethods(PsiMethod method) {
    if (!canHaveSuperMethod(method, true, false)) return PsiMethod.EMPTY_ARRAY;
    Collection<PsiMethod> collection = DeepestSuperMethodsSearch.search(method).findAll();
    return collection.toArray(new PsiMethod[collection.size()]);
  }

  private static Map<MethodSignatureBackedByPsiMethod, HierarchicalMethodSignatureImpl> newbuildMethodHierarchy(PsiClass aClass,
                                                                                                                PsiSubstitutor substitutor,
                                                                                                                final boolean includePrivates,
                                                                                                                final Set<PsiClass> visited) {
    Map<MethodSignatureBackedByPsiMethod, HierarchicalMethodSignatureImpl> result = new LinkedHashMap<MethodSignatureBackedByPsiMethod, HierarchicalMethodSignatureImpl>();
    final Map<MethodSignature, List<PsiMethod>> sameParameterErasureMethods = new THashMap<MethodSignature, List<PsiMethod>>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);

    Map<MethodSignatureBackedByPsiMethod, HierarchicalMethodSignatureImpl> map = new THashMap<MethodSignatureBackedByPsiMethod, HierarchicalMethodSignatureImpl>(new TObjectHashingStrategy<MethodSignatureBackedByPsiMethod>() {
      public int computeHashCode(MethodSignatureBackedByPsiMethod object) {
        return MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY.computeHashCode(object);
      }

      public boolean equals(MethodSignatureBackedByPsiMethod o1, MethodSignatureBackedByPsiMethod o2) {
        if (!MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY.equals(o1, o2)) return false;
        List<PsiMethod> list = sameParameterErasureMethods.get(o1);
        boolean toCheckReturnType = list != null && list.size() > 1;
        if (toCheckReturnType) {
          PsiType returnType1 = o1.getMethod().getReturnType();
          PsiType returnType2 = o2.getMethod().getReturnType();
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
      final MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, substitutor);
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
      PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(superClass, superTypeResolveResult.getSubstitutor(), aClass, substitutor);

      if (!visited.add(superClass)) continue; // cyclic inheritance
      Map<MethodSignatureBackedByPsiMethod, HierarchicalMethodSignatureImpl> superResult = newbuildMethodHierarchy(superClass, finalSubstitutor, false, visited);
      visited.remove(superClass);

      for (MethodSignatureBackedByPsiMethod superSignature : superResult.keySet()) {
        HierarchicalMethodSignatureImpl hierarchicalMethodSignature = superResult.get(superSignature);
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


    for (MethodSignatureBackedByPsiMethod methodSignature : map.keySet()) {
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
    if (existing.getMethod() != superSignature.getMethod()) {
      HierarchicalMethodSignatureImpl copy = copy(superSignature);
      existing.addSuperSignature(copy);
    }
  }

  private static boolean isSuperMethod(PsiClass aClass, HierarchicalMethodSignatureImpl hierarchicalMethodSignature,
                                       HierarchicalMethodSignature superSignatureHierarchical) {
    PsiMethod superMethod = superSignatureHierarchical.getMethod();
    PsiClass superClass = superMethod.getContainingClass();
    if (!superMethod.isConstructor() &&
        !aClass.equals(superClass) &&
        //only public methods from java.lang.Object are considered to be overridden in interface
        !(aClass.isInterface() &&
          "java.lang.Object".equals(superClass.getQualifiedName()) &&
          !superMethod.hasModifierProperty(PsiModifier.PUBLIC)) &&
          PsiUtil.isAccessible(superMethod, aClass, aClass) &&
          MethodSignatureUtil.isSubsignature(superSignatureHierarchical, hierarchicalMethodSignature)) {
      return true;
    }
    return false;
  }

  private static HierarchicalMethodSignatureImpl copy(HierarchicalMethodSignature hi) {
    HierarchicalMethodSignatureImpl hierarchicalMethodSignature = new HierarchicalMethodSignatureImpl(hi);
    for (HierarchicalMethodSignature his : hi.getSuperSignatures()) {
      hierarchicalMethodSignature.addSuperSignature(copy(his));
    }
    return hierarchicalMethodSignature;
  }

  private static PsiSubstitutor obtainFinalSubstitutor(PsiClass superClass,
                                                       PsiSubstitutor superSubstitutor, final PsiClass derivedClass, PsiSubstitutor derivedSubstitutor) {
    PsiTypeParameter[] superTypeParams = superClass.getTypeParameters();
    if (superTypeParams.length == 0) return PsiSubstitutor.EMPTY;

    if (PsiUtil.isRawSubstitutor(derivedClass, derivedSubstitutor)) {
      Map<PsiTypeParameter, PsiType> substitutionMap = derivedSubstitutor.getSubstitutionMap();
      Map<PsiTypeParameter, PsiType> boundSubstituted = new HashMap<PsiTypeParameter, PsiType>();
      boolean boundSubsted = false;
      for (PsiTypeParameter typeParameter : substitutionMap.keySet()) {
        PsiType type = substitutionMap.get(typeParameter);
        PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
        if (type == null && extendsTypes.length != 0) {
          boundSubstituted.put(typeParameter, extendsTypes[0]);
          boundSubsted = true;
        }
        else {
          boundSubstituted.put(typeParameter, type);
        }
      }
      if (boundSubsted) {
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(superClass.getProject()).getElementFactory();
        derivedSubstitutor = elementFactory.createSubstitutor(boundSubstituted);
      }
    }

    final Map<PsiTypeParameter, PsiType> map = superSubstitutor.getSubstitutionMap();
    final Map<PsiTypeParameter, PsiType> m1 = new HashMap<PsiTypeParameter, PsiType>();
    for (PsiTypeParameter typeParameter : superTypeParams) {
      if (map.containsKey(typeParameter)) { //optimization
        PsiType type = superSubstitutor.substitute(typeParameter);
        final PsiType t = ((PsiSubstitutorEx)derivedSubstitutor).substituteNoErase(type);
        m1.put(typeParameter, t);
      }
    }
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(superClass.getProject()).getElementFactory();
    return elementFactory.createSubstitutor(m1);
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
        UserDataHolderEx dataHolder = (UserDataHolderEx)aClass;
        value = dataHolder.putUserDataIfAbsent(SIGNATURES_KEY, value);
      }
    }

    return value.getValue();
  }

  private static class BySignaturesCachedValueProvider implements CachedValueProvider<Map<MethodSignature, HierarchicalMethodSignatureImpl>> {
    private final PsiClass myClass;

    public BySignaturesCachedValueProvider(final PsiClass aClass) {
      myClass = aClass;
    }

    public Result<Map<MethodSignature, HierarchicalMethodSignatureImpl>> compute() {
      //final Map<MethodSignature, Stack<HierarchicalMethodSignatureImpl>> map1 =
      //  new THashMap<MethodSignature, Stack<HierarchicalMethodSignatureImpl>>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);
      //final Map<MethodSignature, Stack<HierarchicalMethodSignatureImpl>> map2 =
      //  new THashMap<MethodSignature, Stack<HierarchicalMethodSignatureImpl>>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);
      //Map<MethodSignature, HierarchicalMethodSignatureImpl> result = new LinkedHashMap<MethodSignature, HierarchicalMethodSignatureImpl>();
      //buildMethodHierarchy(myClass, PsiSubstitutor.EMPTY, new THashSet<PsiClass>(), map1, map2, result, true);



      Map<MethodSignature, HierarchicalMethodSignatureImpl> result = (Map)newbuildMethodHierarchy(myClass, PsiSubstitutor.EMPTY, true, new THashSet<PsiClass>());
      assert result != null;


      return new Result<Map<MethodSignature, HierarchicalMethodSignatureImpl>>(result, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }
  }
}
