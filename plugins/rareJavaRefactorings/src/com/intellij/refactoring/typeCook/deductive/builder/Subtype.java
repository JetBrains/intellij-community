// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.psi.PsiType;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;

public class Subtype extends Constraint {
  public Subtype(PsiType left, PsiType right) {
    super(left, right);
  }

  @Override
  String relationString() {
    return "<:";
  }

  @Override
  int relationType() {
    return 1;
  }

  @Override
  public Constraint apply(final Binding b) {
    return new Subtype(b.apply(myLeft), b.apply(myRight));
  }
}
