package com.jetbrains.python.documentation;

import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.jetbrains.python.psi.PyStringLiteralExpression;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author yole
 */
public class DocStringReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(psiElement(PyStringLiteralExpression.class),
                                        new DocStringReferenceProvider());
  }
}
