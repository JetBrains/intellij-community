package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;

/**
 * Provides a way to sort results of multi-resolve.
 * User: dcheryasov
 * Date: Dec 5, 2008 11:06:30 AM
 */
public class RatedResolveResult implements ResolveResult {
  private final int myRate;
  private final PsiElement myWhat;

  public RatedResolveResult(int rate, PsiElement what) {
    myRate = rate;
    myWhat = what;
  }

  public static final RatedResolveResult[] EMPTY_ARRAY = new RatedResolveResult[0];

  public PsiElement getElement() {
    return myWhat;
  }

  public boolean isValidResult() {
    return true;
  }

  /**
   * Results with higher rate are shown higher in the list of multiResolve().
   * @see com.intellij.psi.PsiPolyVariantReference#multiResolve(boolean)
   * @return desired rate. If in doubt, use 0.
   */
  public int getRate() {
    return myRate;
  }

  public RatedResolveResult replace(PsiElement what) {
    return new RatedResolveResult(myRate, what);
  }

  /**
   * For unusual items that need to be on top.
   */
  public static final int RATE_HIGH = 1000;

  /**
   * For regular references.
   */
  public static final int RATE_NORMAL = 0;

  /**
   * For additional, less important results.
   */
  public static final int RATE_LOW = -1000;
}
