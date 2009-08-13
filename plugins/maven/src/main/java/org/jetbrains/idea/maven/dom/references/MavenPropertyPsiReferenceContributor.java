package org.jetbrains.idea.maven.dom.references;

import com.intellij.patterns.DomPatterns;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.idea.maven.dom.model.MavenDomProperties;

public class MavenPropertyPsiReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    ElementPattern pattern = XmlPatterns.xmlTag().withParent(DomPatterns.withDom(DomPatterns.domElement(MavenDomProperties.class)));
    registrar.registerReferenceProvider(pattern, new MavenPropertyPsiReferenceProvider(), PsiReferenceRegistrar.DEFAULT_PRIORITY);
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(),
                                        new MavenFilteredPropertyPsiReferenceProvider(),
                                        PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }
}
