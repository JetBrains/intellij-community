/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.quickFix.FileReferenceQuickFixProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public boolean isDoNothingOnBind(PsiFile currentFile, final FileReference reference) {
    return false;
  }

  @Nullable
  @NonNls
  public String getRelativePath(Project project, VirtualFile currentFile, VirtualFile dstVFile) {
    return VfsUtil.getPath(currentFile, dstVFile, '/');
  }

  @Nullable
  public PsiDirectory getPsiDirectory(PsiDirectory element) {
    return element;
  }

  public List<? extends LocalQuickFix> registerFixes(HighlightInfo info, FileReference reference) {
    return FileReferenceQuickFixProvider.registerQuickFix(info, reference);
  }

  public PsiDirectory getParentDirectory(final Project project, final PsiDirectory element) {
    return element.getParentDirectory();
  }

  @Nullable
  public PsiDirectory getAbsoluteTopLevelDirLocation(final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      final ProjectFileIndex index = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
      VirtualFile contentRootForFile = index.getSourceRootForFile(virtualFile);
      if (contentRootForFile == null) contentRootForFile = index.getContentRootForFile(virtualFile);

      if (contentRootForFile != null) {
        return file.getManager().findDirectory(contentRootForFile);
      }
    }
    return null;
  }

  @Nullable
  public PsiDirectory getContainingDirectory(PsiFile file) {
    return file.getContainingDirectory();
  }

  @NotNull
  public String trimUrl(@NotNull String url) {
    return url.trim();
  }

  @Nullable
  public PsiReference createDynamicReference(PsiElement element, String str) {
    return null;
  }

  public boolean processVariants(PsiDirectory element, PsiScopeProcessor processor) {
    for (PsiElement child : element.getChildren()) {
      PsiFileSystemItem item = (PsiFileSystemItem)child;
      if (!processor.execute(item, PsiSubstitutor.EMPTY)) return false;
    }
    return true;
  }

}
