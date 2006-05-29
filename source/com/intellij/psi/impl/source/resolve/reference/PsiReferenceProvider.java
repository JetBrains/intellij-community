package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 16:59:58
 * To change this template use Options | File Templates.
 */
public interface PsiReferenceProvider {

  PsiReferenceProvider[] EMPTY_ARRAY = new PsiReferenceProvider[0];

  @NotNull
  PsiReference[] getReferencesByElement(PsiElement element);

  /**
   * @deprecated
   */
  @NotNull
  PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type);

  /**
   * @deprecated
   */
  @NotNull
  PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition);

  void handleEmptyContext(PsiScopeProcessor processor, PsiElement position);
}
