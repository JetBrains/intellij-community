/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 6, 2002
 * Time: 5:41:42 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.impl;

import com.intellij.javaee.JavaeeModuleProperties;
import com.intellij.javaee.VerificationException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.reference.SoftReference;
import gnu.trove.TLongArrayList;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

public class CachedValueImpl<T> implements CachedValue<T> {
  private static final Object NULL = new Object();
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.CachedValueImpl");
  
  private final PsiManager myManager;
  private final CachedValueProvider<T> myProvider;
  private boolean myComputed = false;
  private boolean myTrackValue = true;
  private SoftReference<T> myValue = null;
  private Object[] myDependencies = null;
  private long[] myTimeStamps;
  private long myLastPsiTimeStamp = -1;

  public CachedValueImpl(PsiManager manager, CachedValueProvider<T> provider, boolean trackValue) {
    myManager = manager;
    myProvider = provider;
    myTrackValue = trackValue;
  }

  public void releaseValueIfOutdated() {
    if (!myComputed || isUpToDate()) return;
    myValue = null;
    myComputed = false;
  }

  public T getValue() {
    T value = getUpToDateOrNull();
    if (value != null) {
      return value == NULL ? null : value;
    }

    CachedValueProvider.Result<T> result = myProvider.compute();
    value = result.getValue();
    myValue = new SoftReference<T>(value == null ? (T) NULL : value);
    computeTimeStamps(result.getDependencyItems());

    myComputed = true;
    return value;
  }

  private T getUpToDateOrNull() {
    T value = myValue == null ? null : myValue.get();
    if (myComputed) {
      if (isUpToDate()) {
        return value;
      }
      if (value instanceof Disposable) {
        Disposer.dispose((Disposable)value);
      }
    }
    return null;
  }

  public boolean hasUpToDateValue() {
    return getUpToDateOrNull() != null;
  }

  private boolean isUpToDate() {
    if (myTimeStamps == null) return true;
    if (myManager.isDisposed()) return false;

    for (int i = 0; i < myDependencies.length; i++) {
      Object dependency = myDependencies[i];
      if (dependency == null) continue;
      if (isDependencyOutOfDate(dependency, i)) return false;
    }

    return true;
  }

  private boolean isDependencyOutOfDate(Object dependency, int i) {
    if (dependency instanceof PsiElement &&
        myLastPsiTimeStamp == myManager.getModificationTracker().getModificationCount()) {
      return false;
    }
    final long timeStamp = getTimeStamp(dependency);
    return timeStamp < 0 || timeStamp != myTimeStamps[i];
  }

  private void computeTimeStamps(Object[] dependencies) {
    if (dependencies == null) {
      myTimeStamps = null;
      myDependencies = null;
      return;
    }

    TLongArrayList timeStamps = new TLongArrayList();
    List<Object> deps = new ArrayList<Object>();
    collectDependencies(timeStamps, deps, dependencies);
    if (myTrackValue) {
      collectDependencies(timeStamps, deps, new Object[]{myValue.get()});
    }

    myLastPsiTimeStamp = myManager.getModificationTracker().getModificationCount();
    myTimeStamps = timeStamps.toNativeArray();
    myDependencies = deps.toArray(new Object[deps.size()]);
  }

  private void collectDependencies(TLongArrayList timeStamps, List<Object> resultingDeps, Object[] dependencies) {
    for (Object dependency : dependencies) {
      if (dependency == null || dependency == NULL) continue;
      if (dependency instanceof Object[]) {
        collectDependencies(timeStamps, resultingDeps, (Object[])dependency);
      }
      else {
        resultingDeps.add(dependency);
        timeStamps.add(getTimeStamp(dependency));
      }
    }
  }

  private long getTimeStamp(Object dependency) {
    if (dependency instanceof Reference){
      final Object original = ((Reference)dependency).get();
      if(original == null) return -1;
      return getTimeStamp(original);
    }
    
    if (dependency instanceof Ref) {
      final Object original = ((Ref)dependency).get();
      if(original == null) return -1;
      return getTimeStamp(original);
    }

    if (dependency instanceof ModificationTracker) {
      return ((ModificationTracker)dependency).getModificationCount();
    }

    if (dependency instanceof PsiDirectory) {
      return myManager.getModificationTracker().getOutOfCodeBlockModificationCount();
    }
    
    if (dependency instanceof PsiElement) {
      PsiElement element = (PsiElement)dependency;
      if (!element.isValid()) return -1;
      PsiFile containingFile = element.getContainingFile();
      if (containingFile == null) return -1;
      return containingFile.getModificationStamp();
    }
    else if (dependency instanceof JavaeeModuleProperties) {
      try {
        ((JavaeeModuleProperties)dependency).getMainDeploymentDescriptor().checkIsValid();
      }
      catch (VerificationException e) {
        return -1;
      }
    }

    if (dependency == PsiModificationTracker.MODIFICATION_COUNT) {
      return myManager.getModificationTracker().getModificationCount();
    }
    else if (dependency == PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT) {
      return myManager.getModificationTracker().getOutOfCodeBlockModificationCount();
    } else {
      LOG.error("Wrong dependency type: " + dependency.getClass());
      return -1;
    }
  }

  public CachedValueProvider<T> getValueProvider() {
    return myProvider;
  }
}
