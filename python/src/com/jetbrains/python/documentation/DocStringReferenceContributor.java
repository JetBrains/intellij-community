package com.jetbrains.python.documentation;

import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;

/**
 * @author yole
 */
public class DocStringReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(DocStringTagCompletionContributor.DOCSTRING_PATTERN,
                                        new DocStringReferenceProvider());
  }
}
