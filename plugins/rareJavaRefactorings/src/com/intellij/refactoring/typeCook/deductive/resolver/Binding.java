// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeCook.deductive.resolver;

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVariable;

import java.util.Set;

public abstract class Binding {
  public abstract PsiType apply(PsiType type);

  public abstract PsiType substitute(PsiType type);

  abstract Binding compose(Binding b);

  static final int BETTER = 0;
  static final int WORSE = 1;
  static final int SAME = 2;
  static final int NONCOMPARABLE = 3;

  abstract int compare(Binding b);

  public abstract boolean nonEmpty();

  public abstract boolean isCyclic();

  public abstract Binding reduceRecursive();

  public abstract boolean binds(final PsiTypeVariable var);

  public abstract void merge(Binding b, boolean removeObject);

  public abstract Set<PsiTypeVariable> getBoundVariables();

  public abstract int getWidth();

  public abstract boolean isValid();

  public abstract void addTypeVariable (PsiTypeVariable var);
}
