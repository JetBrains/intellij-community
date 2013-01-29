/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.xml.converters;

import com.intellij.util.xml.Converter;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.paths.PathReferenceManager;
import com.intellij.openapi.paths.PathReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class PathReferenceConverter extends Converter<PathReference> implements CustomReferenceConverter {
  public final static Converter<PathReference> INSTANCE = new PathReferenceConverter();

  public PathReference fromString(@Nullable final String s, final ConvertContext context) {
    final XmlElement element = context.getXmlElement();
    return s == null || element == null ? null : PathReferenceManager.getInstance().getPathReference(s, element);
  }

  public String toString(final PathReference t, final ConvertContext context) {
    return t == null ? null : t.getPath();
  }

  @NotNull
  public PsiReference[] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
    return createReferences(element, true);
  }

  @NotNull
  public PsiReference[] createReferences(@NotNull final PsiElement psiElement, final boolean soft) {
    return PathReferenceManager.getInstance().createReferences(psiElement, soft);
  }
}
