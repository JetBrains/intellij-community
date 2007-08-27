package com.intellij.psi.impl;

import com.intellij.psi.PsiManager;
import com.intellij.psi.util.*;

/**
 * @author ven
 */
public class CachedValuesManagerImpl extends CachedValuesManager {
  private final PsiManager myManager;

  public CachedValuesManagerImpl(PsiManager manager) {
    myManager = manager;
  }

  public <T> CachedValue<T> createCachedValue(CachedValueProvider<T> provider, boolean trackValue) {
    return new CachedValueImpl<T>(myManager, provider, trackValue);
  }

  public <T> ParameterizedCachedValue<T> createParameterizedCachedValue(ParameterizedCachedValueProvider<T> provider, boolean trackValue) {
    return new ParameterizedCachedValueImpl<T>(myManager, provider, trackValue);
  }
}
