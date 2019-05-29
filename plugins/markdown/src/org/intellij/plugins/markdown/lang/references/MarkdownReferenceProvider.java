package org.intellij.plugins.markdown.lang.references;

import com.intellij.openapi.paths.PathReferenceManager;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestinationImpl;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;

public class MarkdownReferenceProvider extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    final PsiElementPattern.Capture<MarkdownLinkDestinationImpl> linkDestinationCapture =
      psiElement(MarkdownLinkDestinationImpl.class).inFile(psiFile(MarkdownFile.class));

    registrar.registerReferenceProvider(linkDestinationCapture, new LinkDestinationReferenceProvider());
  }

  private static class LinkDestinationReferenceProvider extends PsiReferenceProvider {
    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      return PathReferenceManager.getInstance().createReferences(element, false, true, true);
    }
  }
}
