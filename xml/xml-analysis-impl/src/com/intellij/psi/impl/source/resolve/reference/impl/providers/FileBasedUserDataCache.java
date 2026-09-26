// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;

/**
 * @author Maxim.Mossienko
*/
public abstract class FileBasedUserDataCache<T> extends UserDataCache<CachedValue<T>, PsiFile, Object> {
  @Override
  protected CachedValue<T> compute(final PsiFile xmlFile, final Object o) {
    return CachedValuesManager.getManager(xmlFile.getProject()).createCachedValue(
      () -> new CachedValueProvider.Result<>(doCompute(xmlFile), getDependencies(xmlFile)), false);
  }

  protected Object[] getDependencies(PsiFile xmlFile) {
    return new Object[] {xmlFile};
  }

  protected abstract T doCompute(PsiFile file);
  protected abstract Key<CachedValue<T>> getKey();

  public T compute(PsiFile file) {
    final FileViewProvider fileViewProvider = file.getViewProvider();
    final PsiFile baseFile = fileViewProvider.getPsi(fileViewProvider.getBaseLanguage());
    //noinspection ResultOfMethodCallIgnored
    baseFile.getFirstChild(); // expand chameleon out of lock
    return get(getKey(), baseFile, null).getValue();
  }
}
