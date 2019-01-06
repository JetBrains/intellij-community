package com.intellij.bash;

import com.intellij.bash.psi.BashSimpleCommand;
import com.intellij.bash.psi.BashString;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.impl.source.resolve.reference.ArbitraryPlaceUrlReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BashUrlReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(new PsiElementPattern.Capture<PsiElement>(PsiElement.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return (o instanceof BashString && PsiTreeUtil.getParentOfType((PsiElement) o, BashSimpleCommand.class) == null)
            || o instanceof BashSimpleCommand;
      }
    }, new ArbitraryPlaceUrlReferenceProvider(), PsiReferenceRegistrar.LOWER_PRIORITY);
  }
}
