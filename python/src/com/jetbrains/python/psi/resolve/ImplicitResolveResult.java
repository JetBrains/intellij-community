package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ImplicitResolveResult extends RatedResolveResult {
  public ImplicitResolveResult(@Nullable final PsiElement element, final int rate) {
    super(rate, element);
  }
}
