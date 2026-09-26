// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.converters;

import com.intellij.openapi.paths.PathReference;
import com.intellij.openapi.paths.PathReferenceManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class PathReferenceConverter extends Converter<PathReference> implements CustomReferenceConverter {
  public static final Converter<PathReference> INSTANCE = new PathReferenceConverter();

  @Override
  public PathReference fromString(final @Nullable String s, final @NotNull ConvertContext context) {
    final XmlElement element = context.getXmlElement();
    return s == null || element == null ? null : PathReferenceManager.getInstance().getPathReference(s, element);
  }

  @Override
  public String toString(final PathReference t, final @NotNull ConvertContext context) {
    return t == null ? null : t.getPath();
  }

  @Override
  public PsiReference @NotNull [] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
    return createReferences(element, true);
  }

  public PsiReference @NotNull [] createReferences(final @NotNull PsiElement psiElement, final boolean soft) {
    return PathReferenceManager.getInstance().createReferences(psiElement, soft);
  }
}
