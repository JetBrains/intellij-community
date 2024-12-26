// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.references.MavenPathReferenceConverter;

import java.util.Collection;
import java.util.Collections;

public class MavenDependencySystemPathConverter extends ResolvingConverter<PsiFile> implements CustomReferenceConverter {
  @Override
  public PsiFile fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
    if (s == null) return null;
    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(s);
    if (f == null) return null;
    return context.getPsiManager().findFile(f);
  }

  @Override
  public String toString(@Nullable PsiFile file, @NotNull ConvertContext context) {
    if (file == null) return null;
    return file.getVirtualFile().getPath();
  }

  @Override
  public @NotNull Collection<PsiFile> getVariants(@NotNull ConvertContext context) {
    return Collections.emptyList();
  }

  @Override
  public PsiReference @NotNull [] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
    return MavenPathReferenceConverter.createReferences(genericDomValue, element,
                                                        item -> (item instanceof PsiDirectory) || item.getName().endsWith(".jar"), true);
  }
}

