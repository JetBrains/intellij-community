/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Resolves absolute paths from FS root, not content roots
 *
 * @author traff
 */
public class RootFileReferenceSet extends FileReferenceSet {
  public RootFileReferenceSet(String str,
                              @NotNull PsiElement element,
                              int startInElement,
                              PsiReferenceProvider provider,
                              boolean caseSensitive,
                              boolean endingSlashNotAllowed,
                              @Nullable FileType[] suitableFileTypes) {
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

  @NotNull
  @Override
  public Collection<PsiFileSystemItem> computeDefaultContexts() {
    PsiFile file = getContainingFile();
    if (file == null) return ContainerUtil.emptyList();

    if (isAbsolutePathReference() && !ApplicationManager.getApplication().isUnitTestMode()) {
      return toFileSystemItems(ManagingFS.getInstance().getLocalRoots());
    }

    return super.computeDefaultContexts();
  }
}
