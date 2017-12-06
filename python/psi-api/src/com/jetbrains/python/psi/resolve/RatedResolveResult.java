/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.util.containers.SortedList;

import java.util.Comparator;
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

  public PsiElement getElement() {
    return myWhat;
  }

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
    return String.valueOf(myWhat) + "@" + myRate;
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
