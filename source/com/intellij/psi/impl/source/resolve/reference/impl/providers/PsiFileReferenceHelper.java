/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.quickFix.FileReferenceQuickFixProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.lang.LangBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author peter
 */
public class PsiFileReferenceHelper implements FileReferenceHelper{

  @NotNull
  public Class<? extends PsiFileSystemItem> getDirectoryClass() {
    return PsiDirectory.class;
  }

  @NotNull
  public String getDirectoryTypeName() {
    return LangBundle.message("terms.directory");
  }

  @Nullable
  public FileReferenceContext getFileReferenceContext(PsiElement element) {
    if (element instanceof PsiDirectory) {
      return new PsiFileReferenceContext((PsiDirectory)element);
    }
    return null;
  }

  public boolean isTargetAccepted(PsiElement element) {
    return element instanceof PsiFile || element instanceof PsiDirectory;
  }

  public boolean isReferenceTo(PsiElement element, PsiElement myResolve) {
    return element.getManager().areElementsEquivalent(element, myResolve);
  }

  public boolean doNothingOnBind(PsiFile currentFile, final FileReference reference) {
    return false;
  }

  @Nullable
  @NonNls
  public String getRelativePath(Project project, VirtualFile currentFile, VirtualFile dstVFile) {
    return VfsUtil.getPath(currentFile, dstVFile, '/');
  }

  @Nullable
  public PsiDirectory getPsiDirectory(PsiElement element) {
    return element instanceof PsiDirectory ? (PsiDirectory)element : null;
  }

  public void registerQuickfix(HighlightInfo info, FileReference reference) {
    FileReferenceQuickFixProvider.registerQuickFix(info, reference);
  }

  @Nullable
  public PsiElement getAbsoluteTopLevelDirLocation(final PsiFile file) {
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
  public PsiFileSystemItem getContainingDirectory(PsiFile file) {
    return file.getContainingDirectory();
  }

  @NotNull
  public String trimUrl(@NotNull String url) {
    return url.trim();
  }

  @NotNull
  public Collection<? extends PsiReference> createDynamicReference(PsiElement element, String str) {
    return Collections.emptyList();
  }
}
