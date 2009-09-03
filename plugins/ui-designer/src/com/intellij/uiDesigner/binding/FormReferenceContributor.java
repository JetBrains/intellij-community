package com.intellij.uiDesigner.binding;

import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.PsiPlainTextFile;
import static com.intellij.patterns.PlatformPatterns.psiFile;

/**
 * @author yole
 */
public class FormReferenceContributor extends PsiReferenceContributor {
  public void registerReferenceProviders(final PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(psiFile(PsiPlainTextFile.class), new FormReferenceProvider());
  }
}
