/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl;

import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author peter
 */
public abstract class PsiManagerEx extends PsiManager {
  public abstract boolean isBatchFilesProcessingMode();

  public abstract RepositoryManager getRepositoryManager();

  public abstract RepositoryElementsManager getRepositoryElementsManager();

  public abstract boolean isAssertOnFileLoading(VirtualFile file);

  public abstract void nonPhysicalChange();

  public abstract ResolveCache getResolveCache();

  public abstract void registerRunnableToRunOnChange(Runnable runnable);

  public abstract void registerWeakRunnableToRunOnChange(Runnable runnable);

  public abstract void registerRunnableToRunOnAnyChange(Runnable runnable);

  public abstract void registerRunnableToRunAfterAnyChange(Runnable runnable);
}
