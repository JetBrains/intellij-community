// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.util.containers.SortedList;

import java.util.List;

/**
 * Provides a way to sort results of multi-resolve.
 * User: dcheryasov
 */
public class RatedResolveResult implements ResolveResult {
  private final int myRate;
  private final PsiElement myWhat;

  public RatedResolveResult(int rate, PsiElement what) {
    myRate = rate;
    myWhat = what;
  }

  public static final RatedResolveResult[] EMPTY_ARRAY = new RatedResolveResult[0];

  @Override
  public PsiElement getElement() {
    return myWhat;
  }

  @Override
  public boolean isValidResult() {
    return myWhat != null;
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

  @Override
  public String toString() {
    return myWhat + "@" + myRate;
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

  public static List<RatedResolveResult> sorted(List<RatedResolveResult> targets) {
    if (targets.size() == 1) {
      return targets;
    }
    List<RatedResolveResult> ret = new SortedList<>((one, another) -> another.getRate() - one.getRate());
    ret.addAll(targets);
    return ret;
  }
}
