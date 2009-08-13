package org.jetbrains.idea.maven.dom.references;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomReferenceInjector;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

public class MavenPropertyPsiReferenceInjector implements DomReferenceInjector {
  public String resolveString(@Nullable String unresolvedText, @NotNull ConvertContext context) {
    if (StringUtil.isEmptyOrSpaces(unresolvedText)) return unresolvedText;
    MavenDomProjectModel model = (MavenDomProjectModel)DomUtil.getFileElement(context.getInvocationElement()).getRootElement();
    return MavenPropertyResolver.resolve(unresolvedText, model);
  }

  @NotNull
  public PsiReference[] inject(@Nullable String unresolvedText, @NotNull PsiElement element, @NotNull ConvertContext context) {
    return MavenPropertyPsiReferenceProvider.getReferences(element);
  }
}
