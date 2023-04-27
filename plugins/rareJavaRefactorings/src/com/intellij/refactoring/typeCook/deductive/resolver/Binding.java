// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeCook.deductive.resolver;

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVariable;

import java.util.Set;

public abstract class Binding {
  public abstract PsiType apply(PsiType type);

  public abstract PsiType substitute(PsiType type);

  abstract Binding compose(Binding b);

  final static int BETTER = 0;
  final static int WORSE = 1;
  final static int SAME = 2;
  final static int NONCOMPARABLE = 3;

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
