// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class QualifiedRatedResolveResult extends RatedResolveResult implements QualifiedResolveResult {

  private final @NotNull List<PyExpression> myQualifiers;
  private final boolean myIsImplicit;

  public QualifiedRatedResolveResult(@NotNull PsiElement element, @NotNull List<PyExpression> qualifiers, int rate, boolean isImplicit) {
    super(rate, element);
    myQualifiers = qualifiers;
    myIsImplicit = isImplicit;
  }

  @Override
  public @NotNull List<PyExpression> getQualifiers() {
    return myQualifiers;
  }

  @Override
  public boolean isImplicit() {
    return myIsImplicit;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    QualifiedRatedResolveResult result = (QualifiedRatedResolveResult)o;
    return myIsImplicit == result.myIsImplicit && myQualifiers.equals(result.myQualifiers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myQualifiers, myIsImplicit);
  }
}
