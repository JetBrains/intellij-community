package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.containers.WeakHashMap;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Set;

public class ResolveCache {
  private static final Key JAVA_RESOLVE_MAP = Key.create("ResolveCache.JAVA_RESOLVE_MAP");
  private static final Key RESOLVE_MAP = Key.create("ResolveCache.RESOLVE_MAP");
  private static final Key JAVA_RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.JAVA_RESOLVE_MAP_INCOMPLETE");
  private static final Key RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.RESOLVE_MAP_INCOMPLETE");
  private static final Key IS_BEING_RESOLVED_KEY = Key.create("ResolveCache.IS_BEING_RESOLVED_KEY");
  private static final Key VAR_TO_CONST_VALUE_MAP_KEY = Key.create("ResolveCache.VAR_TO_CONST_VALUE_MAP_KEY");

  private static final Object NULL = Key.create("NULL");

  private final PsiManagerImpl myManager;

  private final WeakHashMap myVarToConstValueMap1;

  private final WeakHashMap[] myJavaResolveMaps = new WeakHashMap[4];
  private final WeakHashMap[] myResolveMaps = new WeakHashMap[4];

  private final WeakHashMap myVarToConstValueMap2;

  public static interface GenericsResolver{
    ResolveResult[] resolve(PsiJavaReference ref, boolean incompleteCode);
  }

  public static interface Resolver{
    PsiElement resolve(PsiReference ref, boolean incompleteCode);
  }
  
  public ResolveCache(PsiManagerImpl manager) {
    myManager = manager;

    myVarToConstValueMap1 = getOrCreateWeakMap(myManager, VAR_TO_CONST_VALUE_MAP_KEY, true);
    myJavaResolveMaps[0] = getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP, true);
    myJavaResolveMaps[1] = getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP_INCOMPLETE, true);
    myResolveMaps[0] = getOrCreateWeakMap(myManager, RESOLVE_MAP, true);
    myResolveMaps[1] = getOrCreateWeakMap(myManager, RESOLVE_MAP_INCOMPLETE, true);

    myVarToConstValueMap2 = getOrCreateWeakMap(myManager, VAR_TO_CONST_VALUE_MAP_KEY, false);
    myJavaResolveMaps[2] = getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP, false);
    myJavaResolveMaps[3] = getOrCreateWeakMap(myManager, JAVA_RESOLVE_MAP_INCOMPLETE, false);

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
      ;
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
    myResolveMaps[index].put(ref, new SoftReference(results));
  }

  private Reference<PsiElement> getCachedResolve(PsiReference ref, boolean physical, boolean incompleteCode) {
    int index = getIndex(physical, incompleteCode);
    final Reference<PsiElement> reference = (Reference<PsiElement>)myResolveMaps[index].get(ref);
    if(reference == null) return null;
    return reference;
  }

  public ResolveResult[] resolveWithCaching(PsiJavaReference ref,
                                            GenericsResolver resolver,
                                            boolean needToPreventRecursion,
                                            boolean incompleteCode) {
    ProgressManager.getInstance().checkCanceled();

    synchronized (PsiLock.LOCK) {
      // lock is necessary here because of needToPreventRecursion
      boolean physical = ref.getElement().isPhysical();
      final ResolveResult[] cached = getCachedJavaResolve(ref, physical, incompleteCode);
      if (cached != null) return cached;
      ;
      if (incompleteCode) {
        final ResolveResult[] results = resolveWithCaching(ref, resolver, needToPreventRecursion, false);
        if (results != null && results.length > 0) {
          setCachedJavaResolve(ref, results, physical, true);
          return results;
        }
      }
      if (needToPreventRecursion) {
        PsiElement element = ref.getElement();
        if (element.getUserData(IS_BEING_RESOLVED_KEY) != null) return ResolveResult.EMPTY_ARRAY;
        element.putUserData(IS_BEING_RESOLVED_KEY, "");
      }
      final ResolveResult[] result = resolver.resolve(ref, incompleteCode);
      if (needToPreventRecursion) {
        PsiElement element = ref.getElement();
        element.putUserData(IS_BEING_RESOLVED_KEY, null);
      }
      setCachedJavaResolve(ref, result, physical, incompleteCode);
      return result;
    }
  }

  private int getIndex(boolean physical, boolean ic){
    return (physical ? 0 : 1) << 1 | (ic ? 1 : 0);
  }

  public void setCachedJavaResolve(PsiReference ref, ResolveResult[] result, boolean physical, boolean ic){
    int index = getIndex(physical, ic);
    myJavaResolveMaps[index].put(ref, new SoftReference(result));
  }

  private ResolveResult[] getCachedJavaResolve(PsiReference ref, boolean physical, boolean ic){
    int index = getIndex(physical, ic);
    final Reference reference = (Reference)myJavaResolveMaps[index].get(ref);
    if(reference == null) return null;
    return (ResolveResult[]) reference.get();
  }

  public static interface ConstValueComputer{
    Object execute(PsiVariable variable, Set visitedVars);
  }

  public Object computeConstantValueWithCaching(PsiVariable variable, ConstValueComputer computer, Set visitedVars){
    boolean physical = variable.isPhysical();

    Object cached = (physical ? myVarToConstValueMap1 : myVarToConstValueMap2).get(variable);
    if (cached == NULL) return null;
    if (cached != null) return cached;

    Object result = computer.execute(variable, visitedVars);

    (physical ? myVarToConstValueMap1 : myVarToConstValueMap2).put(variable, result != null ? result : NULL);

    return result;
  }

  public static WeakHashMap getOrCreateWeakMap(final PsiManagerImpl manager, final Key key, boolean forPhysical) {
    MapPair pair = (MapPair)manager.getUserData(key);
    if (pair == null){
      pair = new MapPair();
      manager.putUserData(key, pair);

      final MapPair _pair = pair;
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

  private static class MapPair{
    public WeakHashMap physicalMap;
    public WeakHashMap nonPhysicalMap;

    public MapPair() {
      physicalMap = new WeakHashMap();
      nonPhysicalMap = new WeakHashMap();
    }
  }
}