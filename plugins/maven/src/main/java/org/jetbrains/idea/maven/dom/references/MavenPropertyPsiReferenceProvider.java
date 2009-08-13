package org.jetbrains.idea.maven.dom.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class MavenPropertyPsiReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    return getReferences(element);
  }

  public static PsiReference[] getReferences(PsiElement element) {
    String text = ElementManipulators.getValueText(element);
    int textStart = ElementManipulators.getValueTextRange(element).getStartOffset();
    return getReferences(element, text, textStart, false);
  }

  public static PsiReference[] getReferences(PsiElement element, String text, int textStart, boolean filtered) {
    if (StringUtil.isEmptyOrSpaces(text)) return PsiReference.EMPTY_ARRAY;

    MavenProject mavenProject = MavenDomUtil.findContainingProject(element);
    if (mavenProject == null) return PsiReference.EMPTY_ARRAY;

    List<PsiReference> result = new ArrayList<PsiReference>();

    Matcher matcher = MavenPropertyResolver.PATTERN.matcher(text);
    while (matcher.find()) {
      String propertyName = matcher.group(1);
      int from = textStart + matcher.start(1);
      TextRange range = TextRange.from(from, propertyName.length());

      MavenPropertyPsiReference ref;
      if (filtered) {
        ref = new MavenFilteredPropertyPsiReference(mavenProject, element, propertyName, range);
      }
      else {
        ref = new MavenPropertyPsiReference(mavenProject, element, propertyName, range);
      }
      result.add(ref);
    }

    return result.toArray(new PsiReference[result.size()]);
  }
}
