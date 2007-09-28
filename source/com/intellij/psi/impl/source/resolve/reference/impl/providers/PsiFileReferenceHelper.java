/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.quickFix.FileReferenceQuickFixProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class PsiFileReferenceHelper implements FileReferenceHelper<PsiDirectory> {

  @NotNull
  public Class<PsiDirectory> getDirectoryClass() {
    return PsiDirectory.class;
  }

  @NotNull
  public String getDirectoryTypeName() {
    return LangBundle.message("terms.directory");
  }

  public List<? extends LocalQuickFix> registerFixes(HighlightInfo info, FileReference reference) {
    return FileReferenceQuickFixProvider.registerQuickFix(info, reference);
  }

  public PsiFileSystemItem getPsiFileSystemItem(final Project project, @NotNull final VirtualFile file) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    return file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file);
  }

  public PsiFileSystemItem findRoot(final Project project, @NotNull final VirtualFile file) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile contentRootForFile = index.getSourceRootForFile(file);
    if (contentRootForFile == null) contentRootForFile = index.getContentRootForFile(file);

    if (contentRootForFile != null) {
      return PsiManager.getInstance(project).findDirectory(contentRootForFile);
    }
    return null;
  }

  @NotNull
  public Collection<PsiFileSystemItem> getRoots(@NotNull final Module module) {
    return FilePathReferenceProvider.getRoots(module, false);
  }

  @NotNull
  public Collection<PsiFileSystemItem> getContexts(final Project project, final @NotNull VirtualFile file) {
    final PsiFileSystemItem item = getPsiFileSystemItem(project, file);
    if (item != null) {
      final PsiFileSystemItem parent = item.getParent();
      if (parent != null) {
        final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        final VirtualFile parentFile = parent.getVirtualFile();
        assert parentFile != null;
        VirtualFile root = index.getSourceRootForFile(parentFile);
        if (root != null) {
          final String path = VfsUtil.getRelativePath(parentFile, root, '.');
          final PsiPackage psiPackage = PsiManager.getInstance(project).findPackage(path);
          if (psiPackage != null) {
            final Module module = ModuleUtil.findModuleForFile(file, project);
            assert module != null;
            return Arrays.<PsiFileSystemItem>asList(psiPackage.getDirectories(module.getModuleWithDependenciesScope()));
          }
        }
        return Collections.singleton(parent);
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  public String trimUrl(@NotNull String url) {
    return url.trim();
  }

  @Nullable
  public PsiReference createDynamicReference(PsiElement element, String str) {
    return null;
  }

}
