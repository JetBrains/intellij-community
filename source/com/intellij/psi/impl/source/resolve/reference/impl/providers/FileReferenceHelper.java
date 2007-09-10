/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public interface FileReferenceHelper<T extends PsiFileSystemItem> {

  @NotNull String trimUrl(@NotNull String url);

  @Nullable PsiReference createDynamicReference(PsiElement element, String str);

  @NotNull
  Class<T> getDirectoryClass();

  @NotNull String getDirectoryTypeName();

  @Nullable
  List<? extends LocalQuickFix> registerFixes(HighlightInfo info, FileReference reference);

  @Nullable
  PsiFileSystemItem getPsiFileSystemItem(final Project project, @NotNull VirtualFile file);

  @Nullable
  PsiFileSystemItem findRoot(final Project project, @NotNull VirtualFile file);

  @NotNull
  Collection<PsiFileSystemItem> getRoots(@NotNull Module module);
}
