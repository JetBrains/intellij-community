// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.FileViewProviderFactory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public final class RestFileProviderFactory implements FileViewProviderFactory {

    @Override
    @NotNull
    public FileViewProvider createFileViewProvider(@NotNull VirtualFile virtualFile, Language language, @NotNull PsiManager psiManager, boolean eventSystemEnabled) {
        return new RestFileViewProvider(psiManager, virtualFile, eventSystemEnabled);
    }
}
