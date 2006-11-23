/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author peter
 */
public interface FileReferenceHelper extends QuickFixProvider<FileReference> {
  @NotNull
  Class<? extends PsiFileSystemItem> getDirectoryClass();

  @NotNull String getDirectoryTypeName();

  @Nullable
  FileReferenceContext getFileReferenceContext(PsiElement element);

  boolean isTargetAccepted(PsiElement element);

  boolean isReferenceTo(PsiElement element, PsiElement myResolve);

  boolean doNothingOnBind(PsiFile currentFile, final FileReference reference);

  @Nullable
  @NonNls
  String getRelativePath(Project project, VirtualFile currentFile, VirtualFile dstVFile);

  @Nullable
  PsiDirectory getPsiDirectory(PsiElement element);

  @Nullable
  PsiElement getAbsoluteTopLevelDirLocation(@NotNull final PsiFile file);

  @Nullable
  PsiFileSystemItem getContainingDirectory(PsiFile file);

  @NotNull String trimUrl(@NotNull String url);

  @NotNull
  Collection<? extends PsiReference> createDynamicReference(PsiElement element, String str);

}
