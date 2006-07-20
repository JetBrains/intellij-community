package com.intellij.psi.impl.cache;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.util.Processor;

public interface CacheManager {
  void initialize();
  void dispose();
  CacheUpdater[] getCacheUpdaters();

  PsiFile[] getFilesWithWord(String word, short occurenceMask, GlobalSearchScope scope, final boolean caseSensitively);

  boolean processFilesWithWord(Processor<PsiFile> processor,String word, short occurenceMask, GlobalSearchScope scope, final boolean caseSensitively);

  /**
   * @return all VirtualFile's that contain todo-items under project roots
   */
  PsiFile[] getFilesWithTodoItems();

  /**
   * @return -1 if it's not known
   */
  int getTodoCount(VirtualFile file, final IndexPatternProvider patternProvider);

  /**
   * @return -1 if it's not known
   */
  int getTodoCount(VirtualFile file, IndexPattern pattern);

  /**
   * @deprecated
   */
  void addOrInvalidateFile(VirtualFile file);
}

