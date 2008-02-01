package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.util.Trinity;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 01.04.2003
 * Time: 16:52:28
 * To change this template use Options | File Templates.
 */
public interface ProviderBinding {
  void addAcceptableReferenceProviders(@NotNull PsiElement position, @NotNull List<Trinity<PsiReferenceProvider, ElementPattern, Double>> list);

  void unregisterProvider(final PsiReferenceProvider provider);
}
