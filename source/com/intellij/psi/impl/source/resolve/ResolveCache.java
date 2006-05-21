package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.WeakHashMap;

import java.lang.ref.Reference;
import java.util.*;

public class ResolveCache {
  private static final Key<MapPair<PsiReference, SoftReference<ResolveResult[]>>> JAVA_RESOLVE_MAP = Key.create("ResolveCache.JAVA_RESOLVE_MAP");
  private static final Key<MapPair<PsiReference, Reference<PsiElement>>> RESOLVE_MAP = Key.create("ResolveCache.RESOLVE_MAP");
  private static final Key<MapPair<PsiReference, SoftReference<ResolveResult[]>>> JAVA_RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.JAVA_RESOLVE_MAP_INCOMPLETE");
  private static final Key<MapPair<PsiReference, Reference<PsiElement>>> RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.RESOLVE_MAP_INCOMPLETE");
  private static final Key<List<Thread>> IS_BEING_RESOLVED_KEY = Key.create("ResolveCache.IS_BEING_RESOLVED_KEY");
  private static final Key<MapPair<PsiVariable, Object>> VAR_TO_CONST_VALUE_MAP_KEY = Key.create("ResolveCache.VAR_TO_CONST_VALUE_MAP_KEY");

  private static final Object NULL = Key.create("NULL");

  private final PsiManagerImpl myManager;

  private final Map<PsiVariable,Object> myVarToConstValueMap1;
  private final Map<PsiVariable,Object> myVarToConstValueMap2;

  private final WeakHashMap[] myPolyVariantResolveMaps = new WeakHashMap[4];
  private final WeakHashMap[] myResolveMaps = new WeakHashMap[4];
  private static int ourClearCount = 0;


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
      ourClearCount++;
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

    int clearCountOnStart;
    synchronized (PsiLock.LOCK) {
      clearCountOnStart = ourClearCount;
    }

    boolean physical = ref.getElement().isPhysical();
    final Reference<PsiElement> cached = getCachedResolve(ref, physical, incompleteCode);
    if (cached != null) return cached.get();

    if (incompleteCode) {
      final PsiElement results = resolveWithCaching(ref, resolver, needToPreventRecursion, false);
      if (results != null) {
        setCachedResolve(ref, results, physical, true, clearCountOnStart);
        return results;
      }
    }

    if (!lockElement(ref, needToPreventRecursion)) return null;
    PsiElement result = null;
    try {
      result = resolver.resolve(ref, incompleteCode);
    }
    finally{
      unlockElement(ref, needToPreventRecursion);
    }

    setCachedResolve(ref, result, physical, incompleteCode, clearCountOnStart);
    return result;
  }

  private static boolean lockElement(PsiReference ref, boolean doLock) {
    if (doLock) {
      synchronized (IS_BEING_RESOLVED_KEY) {
        PsiElement elt = ref.getElement();

        List<Thread> lockingThreads = elt.getUserData(IS_BEING_RESOLVED_KEY);
        final Thread currentThread = Thread.currentThread();
        if (lockingThreads == null) {
          lockingThreads = new ArrayList<Thread>(1);
          elt.putUserData(IS_BEING_RESOLVED_KEY, lockingThreads);
        }
        else {
          if (lockingThreads.contains(currentThread)) return false;
        }
        lockingThreads.add(currentThread);
      }
    }
    return true;
  }

  private static void unlockElement(PsiReference ref, boolean doLock) {
    if (doLock) {
      synchronized (IS_BEING_RESOLVED_KEY) {
        PsiElement elt = ref.getElement();

        List<Thread> lockingThreads = elt.getUserData(IS_BEING_RESOLVED_KEY);
        if (lockingThreads == null) return;
        final Thread currentThread = Thread.currentThread();
        lockingThreads.remove(currentThread);
        if (lockingThreads.isEmpty()) {
          elt.putUserData(IS_BEING_RESOLVED_KEY, null);
        }
      }
    }
  }

  private void setCachedResolve(PsiReference ref, PsiElement results, boolean physical, boolean incompleteCode, final int clearCountOnStart) {
    synchronized (PsiLock.LOCK) {
      if (clearCountOnStart != ourClearCount && results != null) return;

      int index = getIndex(physical, incompleteCode);
      myResolveMaps[index].put(ref, new SoftReference<PsiElement>(results));
    }
  }

  //for Visual Fabrique
  public void clearResolveCaches(PsiReference ref) {
    synchronized (PsiLock.LOCK) {
      ourClearCount++;
      final boolean physical = ref.getElement().isPhysical();
      setCachedPolyVariantResolve(ref, null, physical, false, ourClearCount);
      setCachedPolyVariantResolve(ref, null, physical, true, ourClearCount);
    }
  }

  private Reference<PsiElement> getCachedResolve(PsiReference ref, boolean physical, boolean incompleteCode) {
    synchronized (PsiLock.LOCK) {
      int index = getIndex(physical, incompleteCode);
      final Reference<PsiElement> reference = (Reference<PsiElement>)myResolveMaps[index].get(ref);
      if(reference == null) return null;
      return reference;
    }
  }

  public ResolveResult[] resolveWithCaching(PsiPolyVariantReference ref,
                                            PolyVariantResolver resolver,
                                            boolean needToPreventRecursion,
                                            boolean incompleteCode) {
    ProgressManager.getInstance().checkCanceled();

    int clearCountOnStart;
    synchronized (PsiLock.LOCK) {
      clearCountOnStart = ourClearCount;
    }

    boolean physical = ref.getElement().isPhysical();
    final ResolveResult[] cached = getCachedPolyVariantResolve(ref, physical, incompleteCode);
    if (cached != null) return cached;

    if (incompleteCode) {
      final ResolveResult[] results = resolveWithCaching(ref, resolver, needToPreventRecursion, false);
      if (results != null && results.length > 0) {
        setCachedPolyVariantResolve(ref, results, physical, true, clearCountOnStart);
        return results;
      }
    }

    if (!lockElement(ref, needToPreventRecursion)) return JavaResolveResult.EMPTY_ARRAY;
    ResolveResult[] result;
    try {
      result = resolver.resolve(ref, incompleteCode);
    } finally {
      unlockElement(ref, needToPreventRecursion);
    }

    setCachedPolyVariantResolve(ref, result, physical, incompleteCode, clearCountOnStart);
    return result;
  }

  private static int getIndex(boolean physical, boolean ic){
    return (physical ? 0 : 1) << 1 | (ic ? 1 : 0);
  }

  private void setCachedPolyVariantResolve(PsiReference ref, ResolveResult[] result, boolean physical, boolean incomplete, int clearCountOnStart){
    synchronized (PsiLock.LOCK) {
      if (clearCountOnStart != ourClearCount && result != null) return;
      int index = getIndex(physical, incomplete);
      myPolyVariantResolveMaps[index].put(ref, new SoftReference<ResolveResult[]>(result));
    }
  }

  private ResolveResult[] getCachedPolyVariantResolve(PsiReference ref, boolean physical, boolean ic){
    synchronized (PsiLock.LOCK) {
      int index = getIndex(physical, ic);
      final Reference<ResolveResult[]> reference = (Reference<ResolveResult[]>)myPolyVariantResolveMaps[index].get(ref);
      if(reference == null) return null;
      return reference.get();
    }
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
            synchronized (PsiLock.LOCK) {
              ourClearCount++;
              _pair.physicalMap.clear();
            }
          }
        }
      );
      manager.registerRunnableToRunOnAnyChange(
        new Runnable() {
          public void run() {
            synchronized (PsiLock.LOCK) {
              ourClearCount++;
              _pair.nonPhysicalMap.clear();
            }
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