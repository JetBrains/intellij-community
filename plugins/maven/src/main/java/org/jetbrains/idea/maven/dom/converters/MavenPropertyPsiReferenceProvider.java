package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class MavenPropertyPsiReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    return injectReferences(element);
  }

  public static PsiReference[] injectReferences(PsiElement element) {
    String text = ElementManipulators.getValueText(element);
    TextRange range = ElementManipulators.getValueTextRange(element);

    if (StringUtil.isEmptyOrSpaces(text)) return PsiReference.EMPTY_ARRAY;

    List<PsiReference> result = new ArrayList<PsiReference>();

    Matcher matcher = MavenPropertyResolver.PATTERN.matcher(text);
    while (matcher.find()) {
      String propertyName = matcher.group(1);
      int from = range.getStartOffset() + matcher.start(1);

      result.add(new MavenPropertyPsiReference(element, propertyName, TextRange.from(from, propertyName.length())));
    }

    return result.toArray(new PsiReference[result.size()]);
  }
}
