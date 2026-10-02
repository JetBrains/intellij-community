// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Resolves absolute paths from FS root, not content roots
 *
 * @author traff
 */
@ApiStatus.Internal
public class RootFileReferenceSet extends FileReferenceSet {
  public RootFileReferenceSet(String str,
                              @NotNull PsiElement element,
                              int startInElement,
                              PsiReferenceProvider provider,
                              boolean caseSensitive,
                              boolean endingSlashNotAllowed,
                              FileType @Nullable [] suitableFileTypes) {
    super(str, element, startInElement, provider, caseSensitive, endingSlashNotAllowed, suitableFileTypes);
  }

  public RootFileReferenceSet(String s, @NotNull PsiElement element, int offset, PsiReferenceProvider provider, boolean sensitive) {
    super(s, element, offset, provider, sensitive);
  }

  @Override
  public boolean isAbsolutePathReference() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      return FileUtil.isAbsolute(getPathString());
    }
    else {
      return super.isAbsolutePathReference();
    }
  }

  @Override
  public @NotNull Collection<PsiFileSystemItem> computeDefaultContexts() {
    PsiFile file = getContainingFile();
    if (file == null) return ContainerUtil.emptyList();

    if (isAbsolutePathReference() && !ApplicationManager.getApplication().isUnitTestMode()) {
      return toFileSystemItems(ManagingFS.getInstance().getLocalRoots());
    }

    return super.computeDefaultContexts();
  }
}
