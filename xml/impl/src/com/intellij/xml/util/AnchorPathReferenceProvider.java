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
    int offset = getOffset(psiElement, range.getStartOffset(), elementText);
    if (offset == -1) {
      return false;
    }
    
    if (elementText.charAt(offset) == '#') {
      final int pos = elementText.indexOf('?', offset);
      final String anchor = pos == -1 ? elementText.substring(offset + 1, range.getEndOffset()) : elementText.substring(offset + 1, pos);
      references.add(new AnchorReference(anchor, null, psiElement, offset + 1, soft));
      return false;
    }

    FileReference fileReference = findFileReference(references);
    if (fileReference != null && fileReference.resolve() != null) {
      final int i = elementText.indexOf('#', offset);
      if (i >= 0) {
        final int pos = elementText.indexOf('?', i);
        final String anchor = pos == -1 ? elementText.substring(i + 1, range.getEndOffset()) : elementText.substring(i + 1, pos);
        references.add(new AnchorReference(anchor, fileReference, psiElement, range.getStartOffset() + i, soft));
      }
    }
    return false;
  }

  private static int getOffset(final PsiElement psiElement, final int offset, final String elementText) {
    for (DynamicContextProvider provider: Extensions.getExtensions(DynamicContextProvider.EP_NAME)) {
      final int dynamicOffset = provider.getOffset(psiElement, offset, elementText);
      if (dynamicOffset != offset) {
        return dynamicOffset;
      }
    }
    return offset;
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
