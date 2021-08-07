package org.intellij.plugins.markdown.lang.references;

import com.intellij.openapi.paths.PathReference;
import com.intellij.openapi.paths.PathReferenceProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MarkdownAnchorPathReferenceProvider implements PathReferenceProvider {
  @Override
  public boolean createReferences(@NotNull final PsiElement psiElement, final @NotNull List<PsiReference> references, final boolean soft) {
    if (!(psiElement instanceof MarkdownPsiElement)) return false;

    final TextRange range = ElementManipulators.getValueTextRange(psiElement);
    final String elementText = psiElement.getText();
    final int anchorOffset = elementText.indexOf('#');
    if (anchorOffset == -1) return false;

    PsiReference fileReference = null;
    if (range.getStartOffset() != anchorOffset) {
      fileReference = findFileReference(references);
      if (fileReference == null || fileReference.resolve() == null) {
        for (PsiReference reference : references) {
          if (reference instanceof MissingExtensionFileReferenceBase) {
            fileReference = reference;
            break;
          }
        }
        if (fileReference == null || fileReference.resolve() == null) return false;
      }
    }

    final String anchor;
    try {
      int endIndex = range.getEndOffset();
      if (endIndex <= anchorOffset) endIndex = anchorOffset + 1;
      anchor = elementText.substring(anchorOffset + 1, endIndex);
    }
    catch (StringIndexOutOfBoundsException e) {
      throw new RuntimeException(elementText, e);
    }

    references.add(new MarkdownAnchorReferenceImpl(anchor, fileReference, psiElement, anchorOffset + 1));
    return false;
  }

  @Nullable
  static FileReference findFileReference(final List<PsiReference> references) {
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
