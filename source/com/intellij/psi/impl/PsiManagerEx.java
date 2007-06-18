/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

  public abstract FileManager getFileManager();

  public abstract void invalidateFile(PsiFile file);

  public abstract void beforeChildRemoval(final PsiTreeChangeEventImpl event);

  public abstract CacheManager getCacheManager();

  @NotNull
  public abstract List<? extends LanguageInjector> getLanguageInjectors();
}
