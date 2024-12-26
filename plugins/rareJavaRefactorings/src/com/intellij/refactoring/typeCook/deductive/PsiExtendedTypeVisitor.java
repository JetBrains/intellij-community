// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeCook.deductive;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitorEx;
import org.jetbrains.annotations.NotNull;

public abstract class PsiExtendedTypeVisitor<X> extends PsiTypeVisitorEx<X> {
  @Override
  public X visitClassType(final @NotNull PsiClassType classType) {
    super.visitClassType(classType);
    final PsiClassType.ClassResolveResult result = classType.resolveGenerics();

    if (result.getElement() != null) {
      for (final PsiType type : result.getSubstitutor().getSubstitutionMap().values()) {
        if (type != null) {
          type.accept(this);
        }
      }
    }

    return null;
  }
}
