package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ImplicitResolveResult implements RatedResolveResult {
  @Nullable private final PsiElement myElement;

  public ImplicitResolveResult(@Nullable final PsiElement element) {
    myElement = element;
  }

  @Nullable
  public PsiElement getElement() {
    return myElement;
  }

  public boolean isValidResult() {
    return false;
  }

  public int getRate() {
    return RATE_LOW;
  }
}
