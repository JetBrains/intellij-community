package com.intellij.psi.impl.cache;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.TodoPattern;
import com.intellij.psi.PsiFile;

public interface CacheManager {
  void initialize();
  void dispose();
  CacheUpdater[] getCacheUpdaters();

  PsiFile[] getFilesWithWord(String word, short occurenceMask, GlobalSearchScope scope);

  /**
   * @return all VirtualFile's that contain todo-items under project roots
   */
  PsiFile[] getFilesWithTodoItems();

  /**
   * @return -1 if it's not known
   */
  int getTodoCount(VirtualFile file);

  /**
   * @return -1 if it's not known
   */
  int getTodoCount(VirtualFile file, TodoPattern pattern);

  /**
   * @deprecated
   */
  void addOrInvalidateFile(VirtualFile file);
}

