package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.WeakHashMap;

import java.lang.ref.Reference;
import java.util.Set;
import java.util.Collections;
import java.util.Map;

public class ResolveCache {
  private static final Key<MapPair<PsiReference, SoftReference<ResolveResult[]>>> JAVA_RESOLVE_MAP = Key.create("ResolveCache.JAVA_RESOLVE_MAP");
  private static final Key<MapPair<PsiReference, Reference<PsiElement>>> RESOLVE_MAP = Key.create("ResolveCache.RESOLVE_MAP");
  private static final Key<MapPair<PsiReference, SoftReference<ResolveResult[]>>> JAVA_RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.JAVA_RESOLVE_MAP_INCOMPLETE");
  private static final Key<MapPair<PsiReference, Reference<PsiElement>>> RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.RESOLVE_MAP_INCOMPLETE");
  private static final Key<String> IS_BEING_RESOLVED_KEY = Key.create("ResolveCache.IS_BEING_RESOLVED_KEY");
  private static final Key<MapPair<PsiVariable, Object>> VAR_TO_CONST_VALUE_MAP_KEY = Key.create("ResolveCache.VAR_TO_CONST_VALUE_MAP_KEY");

  private static final Object NULL = Key.create("NULL");

  private final PsiManagerImpl myManager;

  private final Map<PsiVariable,Object> myVarToConstValueMap1;
  private final Map<PsiVariable,Object> myVarToConstValueMap2;

  private final WeakHashMap[] myPolyVariantResolveMaps = new WeakHashMap[4];
  private final WeakHashMap[] myResolveMaps = new WeakHashMap[4];


  public static interface PolyVariantResolver {
    ResolveResult[] resolve(PsiPolyVariantReference ref, boolean incompleteCode);
  }

  public static interface Resolver{
    PsiElement resolve(PsiReference ref, boolean incompleteCode);
  }

  public ResolveCache(PsiManagerImpl manager) {
    myManager = manager;

    myVarToConstValueMap1 = Collections.synchronizedMap(getOrCreateWeakMap(myManager, VAR_TO_CONST_VALUE_MAP_KEY, true));
    myVarToConstValueMap2 = Collections.synchronizedMap(getOrCreateWeakMap(myManager, VAR_TO_CONST_VALUE_MAP_KEY, false));

    myPolyVariantResolveMaps[0] = getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP, true);
    myPolyVariantResolveMaps[1] = getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP_INCOMPLETE, true);
    myResolveMaps[0] = getOrCreateWeakMap(myManager, RESOLVE_MAP, true);
    myResolveMaps[1] = getOrCreateWeakMap(myManager, RESOLVE_MAP_INCOMPLETE, true);

    myPolyVariantResolveMaps[2] = getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP, false);
    myPolyVariantResolveMaps[3] = getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP_INCOMPLETE, false);

    myResolveMaps[2] = getOrCreateWeakMap(myManager, RESOLVE_MAP, false);
    myResolveMaps[3] = getOrCreateWeakMap(myManager, RESOLVE_MAP_INCOMPLETE, false);
  }

  public void clearCache() {
    synchronized (PsiLock.LOCK) {
      getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP, true).clear();
      getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP_INCOMPLETE, true).clear();
      getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP, false).clear();
      getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP_INCOMPLETE, false).clear();
      getOrCreateWeakMap(myManager, RESOLVE_MAP, true).clear();
      getOrCreateWeakMap(myManager, RESOLVE_MAP_INCOMPLETE, true).clear();
      getOrCreateWeakMap(myManager, RESOLVE_MAP, false).clear();
      getOrCreateWeakMap(myManager, RESOLVE_MAP_INCOMPLETE, false).clear();
    }
  }

  public PsiElement resolveWithCaching(PsiReference ref,
                                       Resolver resolver,
                                       boolean needToPreventRecursion,
                                       boolean incompleteCode) {
    ProgressManager.getInstance().checkCanceled();

    synchronized (PsiLock.LOCK) {
      // lock is necessary here because of needToPreventRecursion
      boolean physical = ref.getElement().isPhysical();
      final Reference<PsiElement> cached = getCachedResolve(ref, physical, incompleteCode);
      if (cached != null) return cached.get();

      if (incompleteCode) {
        final PsiElement results = resolveWithCaching(ref, resolver, needToPreventRecursion, false);
        if (results != null) {
          setCachedResolve(ref, results, physical, true);
          return results;
        }
      }
      if (needToPreventRecursion) {
        PsiElement element = ref.getElement();
        if (element.getUserData(IS_BEING_RESOLVED_KEY) != null) return null;
        element.putUserData(IS_BEING_RESOLVED_KEY, "");
      }
      final PsiElement result = resolver.resolve(ref, incompleteCode);
      if (needToPreventRecursion) {
        PsiElement element = ref.getElement();
        element.putUserData(IS_BEING_RESOLVED_KEY, null);
      }
      setCachedResolve(ref, result, physical, incompleteCode);
      return result;
    }
  }

  private void setCachedResolve(PsiReference ref, PsiElement results, boolean physical, boolean incompleteCode) {
    int index = getIndex(physical, incompleteCode);
    myResolveMaps[index].put(ref, new SoftReference<PsiElement>(results));
  }

  //for Visual Fabrique
  public void clearResolveCaches(PsiReference ref) {
    final boolean physical = ref.getElement().isPhysical();
    setCachedPolyVariantResolve(ref, null, physical, false);
    setCachedPolyVariantResolve(ref, null, physical, true);
  }

  private Reference<PsiElement> getCachedResolve(PsiReference ref, boolean physical, boolean incompleteCode) {
    int index = getIndex(physical, incompleteCode);
    final Reference<PsiElement> reference = (Reference<PsiElement>)myResolveMaps[index].get(ref);
    if(reference == null) return null;
    return reference;
  }

  public ResolveResult[] resolveWithCaching(PsiPolyVariantReference ref,
                                                PolyVariantResolver resolver,
                                                boolean needToPreventRecursion,
                                                boolean incompleteCode) {
    ProgressManager.getInstance().checkCanceled();

    synchronized (PsiLock.LOCK) {
      // lock is necessary here because of needToPreventRecursion
      boolean physical = ref.getElement().isPhysical();
      final ResolveResult[] cached = getCachedPolyVariantResolve(ref, physical, incompleteCode);
      if (cached != null) return cached;

      if (incompleteCode) {
        final ResolveResult[] results = resolveWithCaching(ref, resolver, needToPreventRecursion, false);
        if (results != null && results.length > 0) {
          setCachedPolyVariantResolve(ref, results, physical, true);
          return results;
        }
      }
      if (needToPreventRecursion) {
        PsiElement element = ref.getElement();
        if (element.getUserData(IS_BEING_RESOLVED_KEY) != null) return JavaResolveResult.EMPTY_ARRAY;
        element.putUserData(IS_BEING_RESOLVED_KEY, "");
      }
      final ResolveResult[] result = resolver.resolve(ref, incompleteCode);
      if (needToPreventRecursion) {
        PsiElement element = ref.getElement();
        element.putUserData(IS_BEING_RESOLVED_KEY, null);
      }
      setCachedPolyVariantResolve(ref, result, physical, incompleteCode);
      return result;
    }
  }

  private static int getIndex(boolean physical, boolean ic){
    return (physical ? 0 : 1) << 1 | (ic ? 1 : 0);
  }

  private void setCachedPolyVariantResolve(PsiReference ref, ResolveResult[] result, boolean physical, boolean incomplete){
    int index = getIndex(physical, incomplete);
    myPolyVariantResolveMaps[index].put(ref, new SoftReference<ResolveResult[]>(result));
  }

  private ResolveResult[] getCachedPolyVariantResolve(PsiReference ref, boolean physical, boolean ic){
    int index = getIndex(physical, ic);
    final Reference<ResolveResult[]> reference = (Reference<ResolveResult[]>)myPolyVariantResolveMaps[index].get(ref);
    if(reference == null) return null;
    return reference.get();
  }

  public static interface ConstValueComputer{
    Object execute(PsiVariable variable, Set<PsiVariable> visitedVars);
  }

  public Object computeConstantValueWithCaching(PsiVariable variable, ConstValueComputer computer, Set<PsiVariable> visitedVars){
    boolean physical = variable.isPhysical();

    Object cached = (physical ? myVarToConstValueMap1 : myVarToConstValueMap2).get(variable);
    if (cached == NULL) return null;
    if (cached != null) return cached;

    Object result = computer.execute(variable, visitedVars);

    (physical ? myVarToConstValueMap1 : myVarToConstValueMap2).put(variable, result != null ? result : NULL);

    return result;
  }

  public static <K,V> WeakHashMap<K,V> getOrCreateWeakMap(final PsiManagerImpl manager, final Key<MapPair<K, V>> key, boolean forPhysical) {
    MapPair<K, V> pair = manager.getUserData(key);
    if (pair == null){
      pair = new MapPair<K,V>();
      manager.putUserData(key, pair);

      final MapPair<K, V> _pair = pair;
      manager.registerRunnableToRunOnChange(
        new Runnable() {
          public void run() {
            //_pair.physicalMap = new WeakHashMap();
            _pair.physicalMap.clear();
          }
        }
      );
      manager.registerRunnableToRunOnAnyChange(
        new Runnable() {
          public void run() {
            //_pair.nonPhysicalMap = new WeakHashMap();
            _pair.nonPhysicalMap.clear();
          }
        }
      );
    }
    return forPhysical ? pair.physicalMap : pair.nonPhysicalMap;
  }

  public static class MapPair<K,V>{
    public WeakHashMap<K,V> physicalMap;
    public WeakHashMap<K,V> nonPhysicalMap;

    public MapPair() {
      physicalMap = new WeakHashMap<K, V>();
      nonPhysicalMap = new WeakHashMap<K, V>();
    }
  }
}