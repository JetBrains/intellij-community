// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlEntityRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface XmlResolveReferenceSupport {
  @Nullable PsiElement resolveReference(PsiReference reference);

  @Nullable XmlEntityDecl resolveReference(XmlEntityRef ref, PsiFile targetFile);

  @Nullable PsiElement resolveSchemaTypeOrElementOrAttributeReference(final @NotNull PsiElement element);
}
