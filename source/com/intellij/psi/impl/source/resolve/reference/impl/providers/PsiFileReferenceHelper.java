/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.quickFix.FileReferenceQuickFixProvider;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.impl.file.PsiDirectoryImpl;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public void registerQuickfix(HighlightInfo info, FileReference reference) {
    FileReferenceQuickFixProvider.registerQuickFix(info, reference);
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

  public PsiFileSystemItem innerResolve(PsiDirectory element, String text, final Condition<String> equalsTo) {
    if (".".equals(text) || "/".equals(text)) {
      return element;
    }
    if ("..".equals(text)) {
      return element.getParentDirectory();
    }
    final PsiFileSystemItem[] processingChildrenResult = new PsiFileSystemItem[1];

    ((PsiDirectoryImpl)element).processChildren(new PsiElementProcessor<PsiFileSystemItem>() {
      public boolean execute(final PsiFileSystemItem element) {
        if (equalsTo.value(element.getName())) {
          processingChildrenResult[0] = element;
          return false;
        }

        return true;
      }
    });
    return processingChildrenResult[0];
  }

  public boolean processVariants(PsiDirectory element, PsiScopeProcessor processor) {
    for (PsiElement child : element.getChildren()) {
      PsiFileSystemItem item = (PsiFileSystemItem)child;
      if (!processor.execute(item, PsiSubstitutor.EMPTY)) return false;
    }
    return true;
  }
}
