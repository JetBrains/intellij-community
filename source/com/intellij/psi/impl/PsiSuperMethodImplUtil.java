package com.intellij.psi.impl;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.Stack;
import gnu.trove.THashMap;
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

  private static void newbuildMethodHierarchy(PsiClass aClass,
                                           PsiSubstitutor substitutor,
                                           Map<Pair<PsiClass,PsiSubstitutor>, Map<MethodSignatureBackedByPsiMethod, HierarchicalMethodSignatureImpl>> visited,
                                           final boolean includePrivates) {
    Pair<PsiClass, PsiSubstitutor> key = Pair.create(aClass, substitutor);
    Map<MethodSignatureBackedByPsiMethod, HierarchicalMethodSignatureImpl> cached = visited.get(key);
    if (cached != null) return;
    cached = new LinkedHashMap<MethodSignatureBackedByPsiMethod, HierarchicalMethodSignatureImpl>();
    visited.put(key, cached);
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

      cached.put(signature, newH);
      map.put(signature, newH);
    }

    for (PsiClassType superType : aClass.getSuperTypes()) {
      PsiClassType.ClassResolveResult superTypeResolveResult = superType.resolveGenerics();
      PsiClass superClass = superTypeResolveResult.getElement();
      if (superClass == null) continue;
      PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(superClass, superTypeResolveResult.getSubstitutor(), substitutor);

      Map<MethodSignatureBackedByPsiMethod, HierarchicalMethodSignatureImpl> superResult = visited.get(Pair.create(superClass, finalSubstitutor));
      if (superResult == null) {
        newbuildMethodHierarchy(superClass, finalSubstitutor, visited, false);
        superResult = visited.get(Pair.create(superClass, finalSubstitutor));
      }
      nextSuperSignature:
      for (MethodSignatureBackedByPsiMethod superSignature : superResult.keySet()) {
        HierarchicalMethodSignatureImpl hierarchicalMethodSignature = superResult.get(superSignature);
        if (!PsiUtil.isAccessible(hierarchicalMethodSignature.getMethod(), aClass, aClass)) continue;
        HierarchicalMethodSignatureImpl existing = map.get(superSignature);
        if (existing == null) {
          map.put(superSignature, copy(hierarchicalMethodSignature));
        }
        else if (isSuperMethod(aClass, existing, hierarchicalMethodSignature)) {
          PsiMethod existingMethod = existing.getMethod();
          if (existingMethod.equals(hierarchicalMethodSignature.getMethod())) continue;
          for (HierarchicalMethodSignature existingSuper : existing.getSuperSignatures()) {
            if (existingSuper.getMethod().equals(hierarchicalMethodSignature.getMethod())) continue nextSuperSignature;
          }
          existing.addSuperSignature(copy(hierarchicalMethodSignature)); //method inherited from class or first interface from extends list is considered to be overriding subsequent signatures
        }
        // just drop an invalid method declaration there - to highlight accordingly
        else if (!cached.containsKey(superSignature)) {
          cached.put(superSignature, hierarchicalMethodSignature);
        }
      }
    }


    for (MethodSignatureBackedByPsiMethod methodSignature : map.keySet()) {
      HierarchicalMethodSignatureImpl hierarchicalMethodSignature = map.get(methodSignature);
      if (cached.get(methodSignature) == null && PsiUtil.isAccessible(hierarchicalMethodSignature.getMethod(), aClass, aClass)) {
        cached.put(methodSignature, hierarchicalMethodSignature);
      }
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

  private static HierarchicalMethodSignatureImpl copy(HierarchicalMethodSignatureImpl hi) {
    HierarchicalMethodSignatureImpl hierarchicalMethodSignature = new HierarchicalMethodSignatureImpl(hi);
    for (HierarchicalMethodSignature his : hi.getSuperSignatures()) {
      hierarchicalMethodSignature.addSuperSignature(his);
    }
    return hierarchicalMethodSignature;
  }

  private static void buildMethodHierarchy(PsiClass aClass,
                                           PsiSubstitutor substitutor,
                                           Set<PsiClass> visited,
                                           Map<MethodSignature, Stack<HierarchicalMethodSignatureImpl>> processing,
                                           Map<MethodSignature, Stack<HierarchicalMethodSignatureImpl>> processed,
                                           Map<MethodSignature, HierarchicalMethodSignatureImpl> result,
                                           final boolean includePrivates) {
    if (!visited.add(aClass)) return;
    final PsiMethod[] methods = aClass.getMethods();
    for (PsiMethod method : methods) {
      if (!includePrivates && method.hasModifierProperty(PsiModifier.PRIVATE)) continue;
      final MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, substitutor);
      final HierarchicalMethodSignatureImpl signatureHierarchical = new HierarchicalMethodSignatureImpl(signature);
      Stack<HierarchicalMethodSignatureImpl> stack = processing.get(signature);
      if (stack != null && !stack.isEmpty()) {
        processStack(stack, aClass, method, signature, signatureHierarchical, result);
      }
      else {
        stack = processed.get(signature);
        if (stack == null) {
          result.put(signature, signatureHierarchical);
          stack = new Stack<HierarchicalMethodSignatureImpl>();
          processing.put(signature, stack);
        }
        else {
          processStack(stack, aClass, method, signature, signatureHierarchical, result);
        }
      }

      stack.push(signatureHierarchical);
    }

    final PsiClassType[] superTypes = aClass.getSuperTypes(); //it is essential that extends list types go before implements list types
    for (final PsiClassType superType : superTypes) {
      final PsiClassType.ClassResolveResult superTypeResolveResult = superType.resolveGenerics();
      PsiClass superClass = superTypeResolveResult.getElement();
      if (superClass == null) continue;
      PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(superClass, superTypeResolveResult.getSubstitutor(), substitutor);
      buildMethodHierarchy(superClass, finalSubstitutor, visited, processing, processed, result, false);
    }

    //move to second stack
    for (final Map.Entry<MethodSignature, Stack<HierarchicalMethodSignatureImpl>> entry : processing.entrySet()) {
      final MethodSignature keySignature = entry.getKey();
      final Stack<HierarchicalMethodSignatureImpl> processingStack = entry.getValue();
      if (!processingStack.isEmpty() && aClass.equals(processingStack.peek().getMethod().getContainingClass())) {
        Stack<HierarchicalMethodSignatureImpl> processedStack = processed.get(keySignature);
        if (processedStack == null) {
          processedStack = new Stack<HierarchicalMethodSignatureImpl>();
          processed.put(keySignature, processedStack);
        }
        while(!processingStack.isEmpty() && aClass.equals(processingStack.peek().getMethod().getContainingClass())) {
          final HierarchicalMethodSignatureImpl signature = processingStack.pop();
          processedStack.push(signature);
        }
      }
    }
  }

  private static void processStack(final Stack<HierarchicalMethodSignatureImpl> stack,
                                   final PsiClass aClass,
                                   final PsiMethod method,
                                   final MethodSignatureBackedByPsiMethod signature,
                                   final HierarchicalMethodSignatureImpl signatureHierarchical,
                                   final Map<MethodSignature, HierarchicalMethodSignatureImpl> result) {
    boolean isNewSignature = true;
    for (int i = stack.size() - 1; i >= 0; i--) {
      HierarchicalMethodSignatureImpl existing = stack.get(i);
      final PsiMethod hisMethod = existing.getMethod();
      final PsiClass hisClass = hisMethod.getContainingClass();
      if (!hisMethod.isConstructor() &&
          !aClass.equals(hisClass) &&
          //only public methods from java.lang.Object are considered to be overridden in interface
          !(hisClass.isInterface() && "java.lang.Object".equals(aClass.getQualifiedName()) && !method.hasModifierProperty(PsiModifier.PUBLIC))
          && PsiUtil.isAccessible(method, hisMethod, hisMethod.getContainingClass())
          && MethodSignatureUtil.isSubsignature(signature, existing)) {
        existing.addSuperSignature(signatureHierarchical);
        isNewSignature = false;
        break;
      }
    }
    if (isNewSignature) {
      result.put(signature, signatureHierarchical);
    }
  }

  private static PsiSubstitutor obtainFinalSubstitutor(PsiClass candidateClass,
                                                       PsiSubstitutor candidateSubstitutor,
                                                       PsiSubstitutor substitutor) {
    final Iterator<PsiTypeParameter> it = PsiUtil.typeParametersIterator(candidateClass);
    if (!it.hasNext()) return PsiSubstitutor.EMPTY;
    final Map<PsiTypeParameter, PsiType> map = candidateSubstitutor.getSubstitutionMap();
    final Map<PsiTypeParameter, PsiType> m1 = new HashMap<PsiTypeParameter, PsiType>();
    while(it.hasNext()) {
      final PsiTypeParameter typeParameter = it.next();
      if (map.containsKey(typeParameter)) { //optimization
        final PsiType t = substitutor.substitute(candidateSubstitutor.substitute(typeParameter));
        m1.put(typeParameter, t);
      }
    }
    PsiElementFactory elementFactory = candidateClass.getManager().getElementFactory();
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


      Map<Pair<PsiClass, PsiSubstitutor>, Map<MethodSignatureBackedByPsiMethod, HierarchicalMethodSignatureImpl>> visited =
        new LinkedHashMap<Pair<PsiClass, PsiSubstitutor>, Map<MethodSignatureBackedByPsiMethod, HierarchicalMethodSignatureImpl>>();
      newbuildMethodHierarchy(myClass, PsiSubstitutor.EMPTY, visited, true);
      Map<MethodSignature, HierarchicalMethodSignatureImpl> result = (Map<MethodSignature, HierarchicalMethodSignatureImpl>)(Map)visited.get(Pair.create(myClass, PsiSubstitutor.EMPTY));
      assert result != null;


      return new Result<Map<MethodSignature, HierarchicalMethodSignatureImpl>>(result, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }
  }
}
