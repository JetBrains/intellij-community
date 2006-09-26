package com.intellij.psi.impl.cache.impl;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class CompositeCacheManager implements CacheManager{
  private List<CacheManager> myManagers = new ArrayList<CacheManager>();

  public void addCacheManager(CacheManager manager) {
    myManagers.add(manager);
  }

  public void initialize() {
    for (CacheManager cacheManager : myManagers) {
      cacheManager.initialize();
    }
  }

  public void dispose() {
    for (CacheManager cacheManager : myManagers) {
      cacheManager.dispose();
    }
  }

  @NotNull
  public CacheUpdater[] getCacheUpdaters() {
    List<CacheUpdater> updaters = new ArrayList<CacheUpdater>();
    for (CacheManager cacheManager : myManagers) {
      updaters.addAll(Arrays.asList(cacheManager.getCacheUpdaters()));
    }
    return updaters.toArray(new CacheUpdater[updaters.size()]);
  }

  @NotNull
  public PsiFile[] getFilesWithWord(@NotNull String word, short occurenceMask, @NotNull GlobalSearchScope scope, final boolean caseSensitively) {
    CommonProcessors.CollectProcessor<PsiFile> processor = new CommonProcessors.CollectProcessor<PsiFile>();
    processFilesWithWord(processor, word, occurenceMask, scope, caseSensitively);
    return processor.toArray(PsiFile.EMPTY_ARRAY);
  }

  public boolean processFilesWithWord(@NotNull Processor<PsiFile> processor, @NotNull String word, short occurenceMask, @NotNull GlobalSearchScope scope, final boolean caseSensitively) {
    for (CacheManager cacheManager : myManagers) {
      if (!cacheManager.processFilesWithWord(processor, word, occurenceMask, scope, caseSensitively)) return false;
    }
    return true;
  }

  @NotNull
  public PsiFile[] getFilesWithTodoItems() {
    List<PsiFile> files = new ArrayList<PsiFile>();
    for (CacheManager cacheManager : myManagers) {
      files.addAll(Arrays.asList(cacheManager.getFilesWithTodoItems()));
    }
    return files.toArray(new PsiFile[files.size()]);
  }

  public int getTodoCount(@NotNull VirtualFile file, final IndexPatternProvider patternProvider) {
    int count = 0;
    for (CacheManager cacheManager : myManagers) {
      count += cacheManager.getTodoCount(file, patternProvider);
    }
    return count;
  }

  public int getTodoCount(@NotNull VirtualFile file, IndexPattern pattern) {
    int count = 0;
    for (CacheManager cacheManager : myManagers) {
      count += cacheManager.getTodoCount(file, pattern);
    }
    return count;
  }

  public void addOrInvalidateFile(@NotNull VirtualFile file) {
    for (CacheManager cacheManager : myManagers) {
      cacheManager.addOrInvalidateFile(file);
    }
  }
}
