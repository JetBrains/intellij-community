package com.intellij.psi.impl.file.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.List;
import java.util.Collection;

public interface FileManager {
  void dispose();

  void runStartupActivity();

  PsiFile findFile(VirtualFile vFile);

  PsiDirectory findDirectory(VirtualFile vFile);

  PsiPackage findPackage(String packageName);

  PsiDirectory[] getRootDirectories(int rootType);

  PsiClass findClass(String qName, GlobalSearchScope scope);
  PsiClass[] findClasses(String qName, GlobalSearchScope scope);

  void reloadFromDisk(PsiFile file); //Q: move to PsiFile(Impl)?

  PsiFile getCachedPsiFile(VirtualFile vFile);

  GlobalSearchScope getResolveScope(PsiElement element);
  GlobalSearchScope getUseScope(PsiElement element);
  Collection<String> getNonTrivialPackagePrefixes();

  void cleanupForNextTest();
}
