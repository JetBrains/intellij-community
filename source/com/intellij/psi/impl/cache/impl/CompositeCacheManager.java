package com.intellij.psi.impl.cache.impl;

import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.TodoPattern;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.ide.startup.CacheUpdater;

import java.util.List;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ArrayList;

/**
 * @author peter
 */
public class CompositeCacheManager implements CacheManager{
  private List<CacheManager> myManagers = new ArrayList<CacheManager>();

  public void addCacheManager(CacheManager manager) {
    myManagers.add(manager);
  }

  public void addProjectComponent(CacheManager cacheManager) {
    if (cacheManager != null) {
      addCacheManager(cacheManager);
    }
  }

  public void initialize() {
    for (int i = 0; i < myManagers.size(); i++) {
      CacheManager cacheManager = myManagers.get(i);
      cacheManager.initialize();
    }
  }

  public void dispose() {
    for (int i = 0; i < myManagers.size(); i++) {
      CacheManager cacheManager = myManagers.get(i);
      cacheManager.dispose();
    }
  }

  public CacheUpdater[] getCacheUpdaters() {
    List<CacheUpdater> updaters = new ArrayList<CacheUpdater>();
    for (int i = 0; i < myManagers.size(); i++) {
      CacheManager cacheManager = myManagers.get(i);
      updaters.addAll(Arrays.asList(cacheManager.getCacheUpdaters()));
    }
    return updaters.toArray(new CacheUpdater[updaters.size()]);
  }

  public PsiFile[] getFilesWithWord(String word, short occurenceMask, GlobalSearchScope scope) {
    List<PsiFile> files = new ArrayList<PsiFile>();
    for (Iterator<CacheManager> iterator = myManagers.iterator(); iterator.hasNext();) {
      CacheManager cacheManager = iterator.next();
      files.addAll(Arrays.asList(cacheManager.getFilesWithWord(word, occurenceMask, scope)));
    }
    return files.toArray(new PsiFile[files.size()]);
  }

  public PsiFile[] getFilesWithTodoItems() {
    List<PsiFile> files = new ArrayList<PsiFile>();
    for (int i = 0; i < myManagers.size(); i++) {
      CacheManager cacheManager = myManagers.get(i);
      files.addAll(Arrays.asList(cacheManager.getFilesWithTodoItems()));
    }
    return files.toArray(new PsiFile[files.size()]);
  }

  public int getTodoCount(VirtualFile file) {
    int count = 0;
    for (int i = 0; i < myManagers.size(); i++) {
      CacheManager cacheManager = myManagers.get(i);
      count += cacheManager.getTodoCount(file);
    }
    return count;
  }

  public int getTodoCount(VirtualFile file, TodoPattern pattern) {
    int count = 0;
    for (int i = 0; i < myManagers.size(); i++) {
      CacheManager cacheManager = myManagers.get(i);
      count += cacheManager.getTodoCount(file, pattern);
    }
    return count;
  }

  public void addOrInvalidateFile(VirtualFile file) {
    for (int i = 0; i < myManagers.size(); i++) {
      CacheManager cacheManager = myManagers.get(i);
      cacheManager.addOrInvalidateFile(file);
    }
  }
}
