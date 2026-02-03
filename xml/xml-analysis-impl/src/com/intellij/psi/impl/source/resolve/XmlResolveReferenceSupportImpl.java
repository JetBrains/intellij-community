// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.impl.UrlReferenceUtil;
import com.intellij.psi.impl.source.resolve.impl.XmlEntityRefUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.SchemaReferencesProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URLReference;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlEntityRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Internal
public class XmlResolveReferenceSupportImpl implements XmlResolveReferenceSupport {
  @Override
  public @Nullable PsiElement resolveReference(PsiReference reference) {
    if (reference instanceof URLReference) {
      return UrlReferenceUtil.resolve((URLReference)reference);
    }
    return null;
  }

  @Override
  public @Nullable XmlEntityDecl resolveReference(XmlEntityRef element, PsiFile targetFile) {
    return XmlEntityRefUtil.resolveEntity(element, targetFile);
  }

  @Override
  public @Nullable PsiElement resolveSchemaTypeOrElementOrAttributeReference(@NotNull PsiElement element) {
    return SchemaReferencesProvider.createTypeOrElementOrAttributeReference(element).resolve();
  }
}
