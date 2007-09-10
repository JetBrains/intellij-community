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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
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
  public String trimUrl(@NotNull String url) {
    return url.trim();
  }

  @Nullable
  public PsiReference createDynamicReference(PsiElement element, String str) {
    return null;
  }

}
