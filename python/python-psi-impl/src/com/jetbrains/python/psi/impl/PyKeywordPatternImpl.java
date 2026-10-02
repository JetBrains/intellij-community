// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyKeywordPattern;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.impl.references.PyKeywordPatternReference;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class PyKeywordPatternImpl extends PyElementImpl implements PyKeywordPattern {
  public PyKeywordPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyKeywordPattern(this);
  }

  @Override
  public PsiReference getReference() {
    return new PyKeywordPatternReference(this);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    final PyPattern valuePattern = getValuePattern();
    return valuePattern != null ? context.getType(valuePattern) : null;
  }
}
