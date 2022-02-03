package org.intellij.plugins.markdown.lang.references;

import com.intellij.openapi.paths.PathReferenceManager;
import com.intellij.openapi.paths.PathReferenceProviderBase;
import com.intellij.openapi.paths.StaticPathReferenceProvider;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;

public class MarkdownReferenceProvider extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    final PsiElementPattern.Capture<MarkdownLinkDestination> linkDestinationCapture =
      psiElement(MarkdownLinkDestination.class).inFile(psiFile(MarkdownFile.class));

    registrar.registerReferenceProvider(linkDestinationCapture, new CommonLinkDestinationReferenceProvider());
    registrar.registerReferenceProvider(linkDestinationCapture, new GithubWikiLocalFileReferenceProvider());
  }

  private static class CommonLinkDestinationReferenceProvider extends PsiReferenceProvider {
    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      return PathReferenceManager.getInstance().createReferences(element, false, true, true);
    }
  }

  static class GithubWikiLocalFileReferenceProvider extends PsiReferenceProvider {
    private static final Pattern LINK_PATTERN = Pattern.compile("^https://github.com/[^/]*/[^/]*/wiki/");
    private static final boolean ARE_REFERENCES_SOFT = false;

    private final MarkdownAnchorPathReferenceProvider myAnchorPathReferenceProvider = new MarkdownAnchorPathReferenceProvider();

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      String text = element.getText();
      Matcher matcher = LINK_PATTERN.matcher(text);
      if (matcher.find()) {
        List<PsiReference> references = new ArrayList<>();

        int offsetInElement = matcher.end();
        int lastPos = PathReferenceProviderBase.getLastPosOfURL(offsetInElement, text);
        String path = lastPos == -1 ? text.substring(offsetInElement) : text.substring(offsetInElement, lastPos);

        StaticPathReferenceProvider staticProvider = new StaticPathReferenceProvider(null);
        staticProvider.setEndingSlashNotAllowed(true);
        staticProvider.setRelativePathsAllowed(false);
        staticProvider.createReferences(element, offsetInElement, path, references, ARE_REFERENCES_SOFT);

        ContentRootRelatedMissingExtensionFileReference.Companion.createReference(element, references, ARE_REFERENCES_SOFT);

        myAnchorPathReferenceProvider.createReferences(element, references, ARE_REFERENCES_SOFT);

        return ContainerUtil.map(references, GithubWikiLocalFileReferenceWrapper::new).toArray(PsiReference.EMPTY_ARRAY);
      }
      else {
        return PsiReference.EMPTY_ARRAY;
      }
    }

    /**
     * see {@link MarkdownUnresolvedFileReferenceInspectionKt#shouldSkip}
     */
    static class GithubWikiLocalFileReferenceWrapper extends PsiReferenceWrapper {
      private GithubWikiLocalFileReferenceWrapper(PsiReference originalPsiReference) {
        super(originalPsiReference);
      }
    }
  }
}
