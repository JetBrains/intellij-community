package com.intellij.psi.impl;

import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class CachedValuesManagerImpl extends CachedValuesManager {
  private final PsiManager myManager;

  public CachedValuesManagerImpl(PsiManager manager) {
    myManager = manager;
  }

  private List<WeakReference<CachedValue>> myValues = new ArrayList<WeakReference<CachedValue>>();
  private int myValuesMaxSize = 0;
  private boolean myReleaseOutdatedInProgress = false;

  public <T> CachedValue<T> createCachedValue(CachedValueProvider<T> provider, boolean trackValue) {
    synchronized (PsiLock.LOCK) {
      final CachedValue<T> value = new CachedValueImpl<T>(myManager, provider, trackValue);
      myValues.add(new WeakReference<CachedValue>(value));
      myValuesMaxSize = Math.max(myValuesMaxSize, myValues.size());
      return value;
    }
  }

  public void releaseOutdatedValues() {
    synchronized (PsiLock.LOCK) {
      if (myReleaseOutdatedInProgress) {
        return;
      }
      myReleaseOutdatedInProgress = true;
      int removed = 0;
      for (int i = 0; i < myValues.size(); i++) {
        WeakReference<CachedValue> ref = myValues.get(i);
        final CachedValue value = ref.get();
        if (value == null) {
          removed++;
          continue;
        }

        value.releaseValueIfOutdated();

        if (removed > 0) {
          myValues.set(i - removed, ref);
        }
      }
      for (int i = 0; i < removed; i++) {
        myValues.remove(myValues.size() - 1);
      }
      if (myValues.size() < myValuesMaxSize / 2) { // makes sense to reallocate
        myValues = new ArrayList<WeakReference<CachedValue>>(myValues);
        myValuesMaxSize = myValues.size();
      }
      ;
    }
  }
}
