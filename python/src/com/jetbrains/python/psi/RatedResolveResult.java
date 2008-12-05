package com.jetbrains.python.psi;

import com.intellij.psi.ResolveResult;

/**
 * Provides a way to sort results of multi-resolve.
 * User: dcheryasov
 * Date: Dec 5, 2008 11:06:30 AM
 */
public interface RatedResolveResult extends ResolveResult {

  RatedResolveResult[] EMPTY_ARRAY = new RatedResolveResult[0];

  /**
   * Results with higher rate are shown higher in the list of multiResolve().
   * @see com.intellij.psi.PsiPolyVariantReference#multiResolve(boolean)
   * @return desired rate. If in doubt, use 0.
   */
  int getRate();

  /**
   * For unusual items that need to be on top.
   */
  int RATE_HIGH = 1000;

  /**
   * For regular references.
   */
  int RATE_NORMAL = 0;

  /**
   * For additional, less important results.
   */
  int RATE_LOW = -1000;
}
