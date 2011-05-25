package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.resolve.RatedResolveResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
* @author yole
*/
public class ResolveResultList extends ArrayList<RatedResolveResult> {
  public static List<? extends RatedResolveResult> to(PsiElement element) {
    if (element== null) {
      return Collections.emptyList();
    }
    final ResolveResultList list = new ResolveResultList();
    list.poke(element, RatedResolveResult.RATE_NORMAL);
    return list;
  }

  // Allows to add non-null elements and discard nulls in a hassle-free way.

  public boolean poke(final PsiElement what, final int rate) {
    if (what == null) return false;
    super.add(new RatedResolveResult(rate, what));
    return true;
  }
}
