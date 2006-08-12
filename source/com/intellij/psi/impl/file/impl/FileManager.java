package com.intellij.psi.impl.file.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface FileManager {
  void dispose();

  void runStartupActivity();

  PsiFile findFile(@NotNull VirtualFile vFile);

  PsiDirectory findDirectory(@NotNull VirtualFile vFile);

  PsiPackage findPackage(@NotNull String packageName);

  PsiDirectory[] getRootDirectories(int rootType);

  PsiClass findClass(@NotNull String qName, @NotNull GlobalSearchScope scope);
  PsiClass[] findClasses(@NotNull String qName, @NotNull GlobalSearchScope scope);

  void reloadFromDisk(@NotNull PsiFile file); //Q: move to PsiFile(Impl)?

  PsiFile getCachedPsiFile(@NotNull VirtualFile vFile);

  @NotNull GlobalSearchScope getResolveScope(@NotNull PsiElement element);
  @NotNull GlobalSearchScope getUseScope(@NotNull PsiElement element);
  Collection<String> getNonTrivialPackagePrefixes();

  void cleanupForNextTest();

  FileViewProvider findViewProvider(VirtualFile file);
  FileViewProvider findCachedViewProvider(VirtualFile file);
  void setViewProvider(VirtualFile virtualFile, FileViewProvider fileViewProvider);

  List<PsiFile> getAllCachedFiles();
}
