// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.openapi.paths.DynamicContextProvider;
import com.intellij.openapi.paths.PathReference;
import com.intellij.openapi.paths.PathReferenceProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class AnchorPathReferenceProvider implements PathReferenceProvider {

  @Override
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
    final AnchorReferenceImpl anchorReference = new AnchorReferenceImpl(anchor, fileReference, psiElement, anchorOffset + 1, soft);
    references.add(anchorReference);
    return false;
  }

  private static boolean isDynamic(final PsiElement psiElement, final int offset, final String elementText) {
    for (DynamicContextProvider provider: DynamicContextProvider.EP_NAME.getExtensionList()) {
      final int dynamicOffset = provider.getOffset(psiElement, offset, elementText);
      if (dynamicOffset != offset) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static FileReference findFileReference(final List<? extends PsiReference> references) {
    FileReference fileReference = null;
    for (PsiReference reference : references) {
      if (reference instanceof FileReference) {
        fileReference = ((FileReference)reference).getFileReferenceSet().getLastReference();
        break;
      }
    }
    return fileReference;
  }

  @Override
  public PathReference getPathReference(@NotNull final String path, @NotNull final PsiElement element) {
    return null;
  }
}
