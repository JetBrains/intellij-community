// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;

import java.util.Objects;

public abstract class Constraint {
  private static final Logger LOG = Logger.getInstance(Constraint.class);

  PsiType myLeft;
  PsiType myRight;

  public Constraint(PsiType left, PsiType right) {
    LOG.assertTrue(left != null, "<null> left type");
    LOG.assertTrue(right != null, "<null> right type");

    myLeft = left;
    myRight = right;
  }

  public PsiType getRight() {
    return myRight;
  }

  public PsiType getLeft() {
    return myLeft;
  }

  abstract String relationString();

  abstract int relationType();

  @Override
  public String toString() {
    return myLeft.getCanonicalText() + " " + relationString() + " " + myRight.getCanonicalText();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof Constraint constraint && Objects.equals(myLeft, constraint.myLeft) && Objects.equals(myRight, constraint.myRight);
  }

  @Override
  public int hashCode() {
    int result;
    result = (myLeft != null ? myLeft.hashCode() : 0);
    result = 29 * result + (myRight != null ? myRight.hashCode() : 0);
    return result + relationType();
  }

  public abstract Constraint apply(final Binding b);
}
