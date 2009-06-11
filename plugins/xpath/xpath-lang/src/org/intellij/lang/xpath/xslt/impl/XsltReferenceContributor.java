package org.intellij.lang.xpath.xslt.impl;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.xml.XmlAttributeValue;
import org.intellij.lang.xpath.xslt.impl.references.XsltReferenceProvider;

/**
 * @author yole
 */
public class XsltReferenceContributor extends PsiReferenceContributor {
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(XmlAttributeValue.class).and(new FilterPattern(new XsltAttributeFilter())),
      new XsltReferenceProvider(registrar.getProject()));
  }
}
