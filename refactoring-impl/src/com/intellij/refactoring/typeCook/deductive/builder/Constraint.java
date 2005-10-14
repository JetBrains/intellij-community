package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.psi.PsiType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Jul 20, 2004
 * Time: 6:00:28 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class Constraint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeCook.deductive.builder.Constraint");

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

  public String toString() {
    return myLeft.getCanonicalText() + " " + relationString() + " " + myRight.getCanonicalText();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Constraint)) return false;

    final Constraint constraint = (Constraint)o;

    if (myLeft != null ? !myLeft.equals(constraint.myLeft) : constraint.myLeft != null) return false;
    if (myRight != null ? !myRight.equals(constraint.myRight) : constraint.myRight != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myLeft != null ? myLeft.hashCode() : 0);
    result = 29 * result + (myRight != null ? myRight.hashCode() : 0);
    return result + relationType();
  }

  public abstract Constraint apply(final Binding b);
}
