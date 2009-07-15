package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomReferenceInjector;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class MavenPropertyReferenceInjector implements DomReferenceInjector {
  public String resolveString(@Nullable String unresolvedText, @NotNull ConvertContext context) {
    if (StringUtil.isEmptyOrSpaces(unresolvedText)) return unresolvedText;
    MavenDomProjectModel model = (MavenDomProjectModel)DomUtil.getFileElement(context.getInvocationElement()).getRootElement();
    return MavenPropertyResolver.resolve(unresolvedText, model);
  }

  @NotNull
  public PsiReference[] inject(@Nullable String unresolvedText, @NotNull PsiElement element, @NotNull ConvertContext context) {
    if (StringUtil.isEmptyOrSpaces(unresolvedText)) return PsiReference.EMPTY_ARRAY;

    List<PsiReference> result = new ArrayList<PsiReference>();
    TextRange range = ElementManipulators.getValueTextRange(element);

    Matcher matcher = MavenPropertyResolver.PATTERN.matcher(unresolvedText);
    while (matcher.find()) {
      String propertyName = matcher.group(1);
      int from = range.getStartOffset() + matcher.start(1);

      result.add(new MavenPropertyPsiReference(element, propertyName, TextRange.from(from, propertyName.length())));
    }

    return result.toArray(new PsiReference[result.size()]);
  }
}
