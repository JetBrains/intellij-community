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
package com.intellij.xml.util;

import com.intellij.openapi.paths.PathReference;
import com.intellij.openapi.paths.PathReferenceProvider;
import com.intellij.openapi.paths.DynamicContextProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class AnchorPathReferenceProvider implements PathReferenceProvider {

  public boolean createReferences(@NotNull final PsiElement psiElement, final @NotNull List<PsiReference> references, final boolean soft) {

    final TextRange range = ElementManipulators.getValueTextRange(psiElement);
    final String elementText = psiElement.getText();
    final int anchorOffset = elementText.indexOf('#');
    if (anchorOffset == -1) {
      return false;
    }
    final boolean dynamic = isDynamic(psiElement, anchorOffset + 1, elementText);
    if (dynamic) {
      return false;
    }

    FileReference fileReference = null;
    if (range.getStartOffset() != anchorOffset) {
      fileReference = findFileReference(references);
      if (fileReference == null || fileReference.resolve() == null) {
        return false;
      }
    }
    final int pos = elementText.indexOf('?', anchorOffset);
    final String anchor;
    try {
      int endIndex = pos != -1 ? pos : range.getEndOffset();
      if (endIndex <= anchorOffset) {
        endIndex = anchorOffset + 1;
      }
      anchor = elementText.substring(anchorOffset + 1, endIndex);
    }
    catch (StringIndexOutOfBoundsException e) {      
      throw new RuntimeException(elementText, e);
    }
    final AnchorReference anchorReference = new AnchorReference(anchor, fileReference, psiElement, anchorOffset + 1, soft);
    references.add(anchorReference);
    return false;
  }

  private static boolean isDynamic(final PsiElement psiElement, final int offset, final String elementText) {
    for (DynamicContextProvider provider: Extensions.getExtensions(DynamicContextProvider.EP_NAME)) {
      final int dynamicOffset = provider.getOffset(psiElement, offset, elementText);
      if (dynamicOffset != offset) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static FileReference findFileReference(final List<PsiReference> references) {
    FileReference fileReference = null;
    for (PsiReference reference : references) {
      if (reference instanceof FileReference) {
        fileReference = ((FileReference)reference).getFileReferenceSet().getLastReference();
        break;
      }
    }
    return fileReference;
  }

  public PathReference getPathReference(@NotNull final String path, @NotNull final PsiElement element) {
    return null;
  }
}
