package org.jetbrains.yaml.psi;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;

/**
 * @author oleg
 */
public class YAMLReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(final PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(YAMLKeyValue.class), new YAMLReferenceProvider());
  }
}
