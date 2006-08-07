package com.intellij.psi.impl;

import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.WeakList;

import java.util.List;

/**
 * @author ven
 */
public class CachedValuesManagerImpl extends CachedValuesManager {
  private final PsiManager myManager;

  public CachedValuesManagerImpl(PsiManager manager) {
    myManager = manager;
  }

  private List<CachedValue> myValues = new WeakList<CachedValue>();
  private int myValuesMaxSize = 0;
  private boolean myReleaseOutdatedInProgress = false;

  public <T> CachedValue<T> createCachedValue(CachedValueProvider<T> provider, boolean trackValue) {
    synchronized (PsiLock.LOCK) {
      final CachedValue<T> value = new CachedValueImpl<T>(myManager, provider, trackValue);
      myValues.add(value);
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
      for (final CachedValue value : myValues) {
        value.releaseValueIfOutdated();
      }
      myReleaseOutdatedInProgress = false;
    }
  }
}
